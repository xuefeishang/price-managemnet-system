package com.pricemanagement.service;

import com.pricemanagement.entity.Department;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.DepartmentRepository;
import com.pricemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public List<Department> getAllDepartments() {
        return departmentRepository.findAll();
    }

    public Optional<Department> getDepartmentById(Long id) {
        return departmentRepository.findById(id);
    }

    public Optional<Department> getDepartmentByCode(String deptCode) {
        return departmentRepository.findByDeptCode(deptCode);
    }

    public List<Department> getActiveDepartments() {
        return departmentRepository.findAllActive();
    }

    /**
     * 获取部门树（树状结构）
     */
    public List<Department> getDepartmentTree() {
        List<Department> allDepartments = departmentRepository.findAllActive();

        // 获取所有部门负责人信息
        List<Long> leaderIds = allDepartments.stream()
                .filter(d -> d.getLeaderId() != null)
                .map(Department::getLeaderId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> leaderNameMap = Map.of();
        if (!leaderIds.isEmpty()) {
            List<User> leaders = userRepository.findAllById(leaderIds);
            leaderNameMap = leaders.stream()
                    .collect(Collectors.toMap(User::getId, u -> u.getNickname() != null ? u.getNickname() : u.getUsername()));
        }

        // 获取每个部门的用户数
        Map<Long, Long> userCountMap = allDepartments.stream()
                .collect(Collectors.toMap(Department::getId, d -> departmentRepository.countUsersByDeptId(d.getId())));

        // 构建树
        Map<Long, List<Department>> parentMap = allDepartments.stream()
                .collect(Collectors.groupingBy(d -> d.getParentId() != null ? d.getParentId() : 0L));

        List<Department> roots = parentMap.getOrDefault(0L, new ArrayList<>());

        for (Department root : roots) {
            root.setLeaderName(leaderNameMap.get(root.getLeaderId()));
            root.setUserCount(userCountMap.getOrDefault(root.getId(), 0L).intValue());
            buildTree(root, parentMap, leaderNameMap, userCountMap);
        }

        return roots;
    }

    private void buildTree(Department parent, Map<Long, List<Department>> parentMap,
                           Map<Long, String> leaderNameMap, Map<Long, Long> userCountMap) {
        List<Department> children = parentMap.getOrDefault(parent.getId(), new ArrayList<>());
        for (Department child : children) {
            child.setLeaderName(leaderNameMap.get(child.getLeaderId()));
            child.setUserCount(userCountMap.getOrDefault(child.getId(), 0L).intValue());
            buildTree(child, parentMap, leaderNameMap, userCountMap);
        }
        parent.setChildren(children);
    }

    /**
     * 获取扁平列表（带层级信息）
     */
    public List<Department> getFlatList() {
        return departmentRepository.findAllActive();
    }

    /**
     * 创建部门
     */
    @Transactional
    public Department createDepartment(Department department) {
        if (departmentRepository.existsByDeptCode(department.getDeptCode())) {
            throw new IllegalArgumentException("部门编码已存在: " + department.getDeptCode());
        }

        // 设置排序
        if (department.getSortOrder() == null) {
            Integer maxSortOrder = department.getParentId() != null
                    ? departmentRepository.findMaxSortOrderByParentId(department.getParentId())
                    : departmentRepository.findMaxSortOrderForRoot();
            department.setSortOrder(maxSortOrder != null ? maxSortOrder + 1 : 1);
        }

        // 先保存获取ID
        Department saved = departmentRepository.save(department);

        // 设置层级路径（需要ID）
        if (saved.getParentId() != null) {
            Long parentId = saved.getParentId();
            Department parent = departmentRepository.findById(parentId)
                    .orElseThrow(() -> new IllegalArgumentException("父部门不存在: " + parentId));
            saved.setLevel(parent.getLevel() + 1);
            saved.setPath(parent.getPath() + "/" + saved.getId());
        } else {
            saved.setLevel(1);
            saved.setPath(String.valueOf(saved.getId()));
        }

        // 再次保存更新路径
        saved = departmentRepository.save(saved);

        log.info("Created department: {} with path: {}", saved.getDeptCode(), saved.getPath());
        return saved;
    }

    /**
     * 更新部门
     */
    @Transactional
    public Department updateDepartment(Long id, Department department) {
        Department existing = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在: " + id));

        if (department.getDeptName() != null) {
            existing.setDeptName(department.getDeptName());
        }
        if (department.getDeptType() != null) {
            existing.setDeptType(department.getDeptType());
        }
        if (department.getLeaderId() != null) {
            existing.setLeaderId(department.getLeaderId());
        }
        if (department.getSortOrder() != null) {
            existing.setSortOrder(department.getSortOrder());
        }
        if (department.getStatus() != null) {
            existing.setStatus(department.getStatus());
        }

        Department saved = departmentRepository.save(existing);
        log.info("Updated department: {}", saved.getDeptCode());
        return saved;
    }

    /**
     * 移动部门（修改父部门）
     */
    @Transactional
    public Department moveDepartment(Long id, Long newParentId) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在: " + id));

        // 不能移动到自己或自己的子部门下
        if (newParentId != null) {
            if (newParentId.equals(id)) {
                throw new IllegalArgumentException("不能将部门移动到自己下面");
            }
            Department newParent = departmentRepository.findById(newParentId)
                    .orElseThrow(() -> new IllegalArgumentException("目标部门不存在: " + newParentId));
            if (newParent.getPath().startsWith(department.getPath() + "/") || newParent.getPath().equals(department.getPath())) {
                throw new IllegalArgumentException("不能将部门移动到自己的子部门下面");
            }
            department.setParentId(newParentId);
            department.setLevel(newParent.getLevel() + 1);
            department.setPath(newParent.getPath() + "/" + department.getId());
        } else {
            department.setParentId(null);
            department.setLevel(1);
            department.setPath(String.valueOf(department.getId()));
        }

        // 更新子部门的路径
        updateChildrenPath(department);

        Department saved = departmentRepository.save(department);
        log.info("Moved department {} to parent {}", saved.getDeptCode(), newParentId);
        return saved;
    }

    /**
     * 更新子部门路径
     */
    private void updateChildrenPath(Department parent) {
        List<Department> children = departmentRepository.findByParentIdOrderBySortOrderAsc(parent.getId());
        for (Department child : children) {
            child.setLevel(parent.getLevel() + 1);
            child.setPath(parent.getPath() + "/" + child.getId());
            updateChildrenPath(child);
            departmentRepository.save(child);
        }
    }

    /**
     * 批量排序
     */
    @Transactional
    public void batchSort(List<Long> orderedIds) {
        if (orderedIds == null || orderedIds.isEmpty()) {
            return;
        }

        // 批量查询所有部门
        List<Department> departments = departmentRepository.findAllById(orderedIds);
        Map<Long, Department> deptMap = departments.stream()
                .collect(Collectors.toMap(Department::getId, d -> d));

        // 批量更新排序
        List<Department> toSave = new ArrayList<>();
        for (int i = 0; i < orderedIds.size(); i++) {
            Department dept = deptMap.get(orderedIds.get(i));
            if (dept != null) {
                dept.setSortOrder(i + 1);
                toSave.add(dept);
            }
        }
        departmentRepository.saveAll(toSave);
        log.info("Batch sorted {} departments", toSave.size());
    }

    /**
     * 删除部门
     */
    @Transactional
    public void deleteDepartment(Long id) {
        Department department = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("部门不存在: " + id));

        // 检查是否有子部门
        List<Department> children = departmentRepository.findByParentIdOrderBySortOrderAsc(id);
        if (!children.isEmpty()) {
            throw new IllegalArgumentException("该部门下还有子部门，请先删除子部门");
        }

        // 检查是否有用户
        Long userCount = departmentRepository.countUsersByDeptId(id);
        if (userCount > 0) {
            throw new IllegalArgumentException("该部门下还有 " + userCount + " 个用户，请先转移用户");
        }

        departmentRepository.deleteById(id);
        log.info("Deleted department: {}", department.getDeptCode());
    }
}
