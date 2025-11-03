package codesAndStandards.springboot.userApp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "bookmarks",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "UK_user_document",
//                        columnNames = {"user_id", "document_id", "page_number"}
                        columnNames = {"user_id", "document_id"}
                )
        }
)
public class Bookmark {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "bookmark_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_bookmarks_users")
    )
    @OnDelete(action = OnDeleteAction.CASCADE) // ✅ deletes bookmarks when user deleted
    private User user;

//    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "document_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "FK_bookmarks_documents")
    )
    @OnDelete(action = OnDeleteAction.CASCADE) // ✅ deletes bookmarks when document deleted
    private Document document;

//    @Column(name = "page_number", nullable = false)
//    private Integer pageNumber;

    @Column(name = "bookmark_name", length = 255)
    private String bookmarkName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
