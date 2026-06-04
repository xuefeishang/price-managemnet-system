
package com.pricemanagement.repository;

import com.pricemanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    List<User> findByUsernameIn(Collection<String> usernames);

    @Query("SELECT u FROM User u WHERE u.username LIKE %:keyword% OR u.nickname LIKE %:keyword%")
    List<User> findByUsernameOrNicknameContaining(@Param("keyword") String keyword);

    boolean existsByUsername(String username);

    Optional<User> findByEmployeeId(String employeeId);

    boolean existsByEmployeeId(String employeeId);

    Optional<User> findByWechatOpenid(String wechatOpenid);

    List<User> findByStatus(com.pricemanagement.constants.CommonStatus status);
}
