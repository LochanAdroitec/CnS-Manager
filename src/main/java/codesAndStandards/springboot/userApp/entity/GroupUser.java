package codesAndStandards.springboot.userApp.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "GroupUser",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_GroupUser", columnNames = {"user_id", "groupId"})
        }
)
public class GroupUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "groupUserId")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_GroupUser_User")
    )
    private User user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(
            name = "groupId",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_GroupUser_Group")
    )
    private Group group;
}
