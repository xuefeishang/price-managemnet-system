package com.pricemanagement.service;

import com.pricemanagement.entity.SysRole;
import com.pricemanagement.repository.SysRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class ActiveRoleResolver {

    private static final String ACTIVE = "ACTIVE";

    private final SysRoleRepository sysRoleRepository;

    public SysRole requireActiveByCode(String roleCode) {
        return sysRoleRepository.findByRoleCodeAndStatus(roleCode, ACTIVE)
                .orElseThrow(() -> new IllegalArgumentException("角色不存在或未启用"));
    }

    public List<SysRole> requireAllActiveByIds(Collection<Long> roleIds) {
        if (roleIds != null && roleIds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("包含不存在或未启用的角色");
        }
        List<Long> distinctIds = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        List<SysRole> roles = distinctIds.isEmpty()
                ? List.of()
                : sysRoleRepository.findByIdInAndStatus(distinctIds, ACTIVE);
        if (roles.size() != distinctIds.size()) {
            throw new IllegalArgumentException("包含不存在或未启用的角色");
        }
        return roles;
    }

    public List<Long> activeRoleIds(Collection<Long> roleIds) {
        if (roleIds == null || roleIds.isEmpty()) {
            return List.of();
        }
        return sysRoleRepository.findByIdInAndStatus(roleIds, ACTIVE).stream().map(SysRole::getId).toList();
    }
}
