package codesAndStandards.springboot.userApp.entity;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "GroupDocument",
        uniqueConstraints = {
                @UniqueConstraint(name = "UQ_GroupDocument", columnNames = {"document_id", "groupId"})
        }
)
public class GroupDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "groupDocumentId")
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(
            name = "document_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_GroupDocument_Document")
    )
    private Document document;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(
            name = "groupId",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_GroupDocument_Group")
    )
    private Group group;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(
            name = "user_id",
            foreignKey = @ForeignKey(name = "FK_GroupDocument_User")
    )
    private User assignedBy; // userId (optional)
}
