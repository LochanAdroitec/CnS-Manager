package codesAndStandards.springboot.userApp.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(
        name = "AccessControlLogic",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UQ_AccessControlLogic",
                        columnNames = {"document_id", "groupId"}
                )
        }
)
public class AccessControlLogic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "accessId")
    private Long accessId;

    // FK: Document
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "document_id",
            referencedColumnName = "document_id",
            foreignKey = @ForeignKey(name = "FK_AccessControlLogic_Document")
    )
    private Document document;

    // FK: Group
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "groupId",
            referencedColumnName = "groupId",
            foreignKey = @ForeignKey(name = "FK_AccessControlLogic_Group")
    )
    private Group group;

    // FK: Created By User
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            nullable = true,
            referencedColumnName = "user_id",
            foreignKey = @ForeignKey(name = "FK_AccessControlLogic_User")
    )
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
