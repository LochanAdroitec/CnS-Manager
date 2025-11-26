package codesAndStandards.springboot.userApp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "tags",
        indexes = {
                @Index(name = "idx_tags_created_by", columnList = "created_by"),
                @Index(name = "idx_tags_updated_by", columnList = "updated_by"),
                @Index(name = "idx_tags_created_at", columnList = "created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_tags_tag_name", columnNames = "tag_name")
        }
)
public class Tag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long id;

    @Column(name = "tag_name", nullable = false, unique = true, length = 50)
    private String tagName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_tags_created_by")
    )
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = true, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "updated_by",
            nullable = true,
            foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT)
    )
    private User updatedBy;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // REMOVED mappedBy - this side now owns the relationship
    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "DocumentTags",
            joinColumns = @JoinColumn(name = "tag_id"),
            inverseJoinColumns = @JoinColumn(name = "document_id")
    )
    private Set<Document> documents = new HashSet<>();
}