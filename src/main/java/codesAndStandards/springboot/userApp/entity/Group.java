package codesAndStandards.springboot.userApp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Groups")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "groupId")
    private Long id;

    @Column(name = "groupName", nullable = false)
    private String groupName;

    @Column(name = "description", length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(
            name = "group_createdBy",
            foreignKey = @ForeignKey(name = "FK_Groups_CreatedBy")
    )
    private User createdBy;

    @Column(name = "group_createdAt")
    private LocalDateTime createdAt = LocalDateTime.now();

    // One group → many GroupUser
    @OneToMany(mappedBy = "group", orphanRemoval = true)
    private Set<GroupUser> groupUsers = new HashSet<>();

    // One group → many GroupDocument
    @OneToMany(mappedBy = "group", orphanRemoval = true)
    private Set<GroupDocument> groupDocument = new HashSet<>();
}
