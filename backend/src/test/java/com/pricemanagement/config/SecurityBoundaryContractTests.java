package com.pricemanagement.config;

import com.pricemanagement.constants.SystemConstants;
import com.pricemanagement.controller.DepartmentController;
import com.pricemanagement.controller.PermissionController;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SecurityBoundaryContractTests {

    @Test
    void protectedManagementEndpointsAreNotPublicPaths() {
        Set<String> publicPaths = Set.copyOf(Arrays.asList(SystemConstants.PUBLIC_PATHS));

        assertFalse(publicPaths.contains("/api/menus/tree"));
        assertFalse(publicPaths.contains("/api/menus/visible"));
        assertFalse(publicPaths.contains("/api/departments/tree"));
        assertFalse(publicPaths.contains("/api/departments"));
        assertFalse(publicPaths.contains("/api/permissions"));
        assertFalse(publicPaths.contains("/api/permissions/tree"));
    }

    @Test
    void permissionTreeIsAdminOnly() throws NoSuchMethodException {
        Method method = PermissionController.class.getMethod("getPermissionTree");
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);

        assertEquals("hasRole('ADMIN')", annotation.value());
    }

    @Test
    void departmentReadEndpointsAreAdminOnly() throws NoSuchMethodException {
        assertEquals("hasRole('ADMIN')",
                DepartmentController.class.getMethod("getDepartmentTree").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('ADMIN')",
                DepartmentController.class.getMethod("getAllDepartments").getAnnotation(PreAuthorize.class).value());
        assertEquals("hasRole('ADMIN')",
                DepartmentController.class.getMethod("getDepartment", Long.class).getAnnotation(PreAuthorize.class).value());
    }
}
