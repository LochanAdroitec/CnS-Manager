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
        name = "classification",
        indexes = {
                @Index(name = "idx_classification_created_by", columnList = "created_by"),
                @Index(name = "idx_classification_updated_by", columnList = "updated_by"),
                @Index(name = "idx_classification_created_at", columnList = "created_at")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_classification_name", columnNames = "classification_name")
        }
)
public class Classification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "classification_id")
    private Long id;

    @Column(name = "classification_name", nullable = false, unique = true, length = 100)
    private String classificationName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "created_by",
            nullable = true,
            foreignKey = @ForeignKey(name = "fk_classification_created_by")
    )
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
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

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    @JoinTable(
            name = "DocumentClassifications",
            joinColumns = @JoinColumn(name = "classification_id"),
            inverseJoinColumns = @JoinColumn(name = "document_id")
    )
    private Set<Document> documents = new HashSet<>();
}