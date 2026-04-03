package com.pricemanagement.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "menu_item", indexes = {
    @Index(name = "idx_menu_parent", columnList = "parent_id"),
    @Index(name = "idx_menu_sort", columnList = "sort_order")
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "parent_id")
    private Long parentId;

    @Transient
    @ToString.Exclude
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private MenuItem parent;

    @Transient
    @ToString.Exclude
    private List<MenuItem> children = new ArrayList<>();

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 200)
    private String path;

    @Column(length = 50)
    private String icon;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(nullable = false)
    private Boolean visible = true;

    @Column(length = 500)
    private String roles; // JSON array format: ["ADMIN","EDITOR","VIEWER"]

    @CreationTimestamp
    @Column(name = "created_time", nullable = false, updatable = false)
    private LocalDateTime createdTime;

    @UpdateTimestamp
    @Column(name = "updated_time")
    private LocalDateTime updatedTime;

    public void addChild(MenuItem child) {
        children.add(child);
        child.setParentId(this.id);
    }

    public void removeChild(MenuItem child) {
        children.remove(child);
        child.setParentId(null);
    }
}
