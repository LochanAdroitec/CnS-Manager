package codesAndStandards.springboot.userApp.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "users",
        // Custom names for unique constraints
        uniqueConstraints = {
                @UniqueConstraint(name = "UK_users_email", columnNames = "email"),
                @UniqueConstraint(name = "UK_users_username", columnNames = "username")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(name = "email", nullable = false)
    private String email;

    // Many users -> one role
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_users_role"))
    private Role role;

    @Column(name = "created_at", columnDefinition = "DATETIME")
    private LocalDateTime createdAt = LocalDateTime.now();

    // Self-referencing relationship (created by another user)
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "created_by",
            foreignKey = @ForeignKey(name = "FK_users_created_by"))
    private User createdBy;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
//    @OneToMany(mappedBy = "user", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private Set<Bookmark> bookmarks = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = {}, orphanRemoval = true)
    private List<ActivityLog> activityLogs = new ArrayList<>();

    @OneToMany(mappedBy = "createdBy")
    private Set<Tag> createdTags = new HashSet<>();

    @OneToMany(mappedBy = "updatedBy")
    private Set<Tag> updatedTags = new HashSet<>();

    @OneToMany(mappedBy = "createdBy")
    private Set<Classification> createdClassifications = new HashSet<>();

    @OneToMany(mappedBy = "updatedBy")
    private Set<Classification> updatedClassifications = new HashSet<>();

    @Transient
    private List<Long> groupIds;

    @PreRemove
    private void preRemove() {
        // Set all references to null before deletion
        for (Tag tag : createdTags) {
            tag.setCreatedBy(null);
        }
        for (Tag tag : updatedTags) {
            tag.setUpdatedBy(null);
        }
        for (Classification classification : createdClassifications) {
            classification.setCreatedBy(null);
        }
        for (Classification classification : updatedClassifications) {
            classification.setUpdatedBy(null);
        }
    }

    @OneToMany(mappedBy = "user", fetch = FetchType.EAGER)
    private Set<GroupUser> groupUsers = new HashSet<>();

}
