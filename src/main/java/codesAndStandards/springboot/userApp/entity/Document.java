package codesAndStandards.springboot.userApp.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
//import org.aspectj.apache.bcel.generic.Tag;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "Documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "document_id")
    private Long id;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "product_code", nullable = false)
    private String productCode;

    @Column(name = "edition")
    private String edition;

    //TODO: publish date is to be changed

    // ✅ Now stores YYYY or YYYY-MM as a string (month optional)
    @Column(name = "publication_date", length = 7)
    private String publishDate;

//    @Column(name = "publication_date")
//    private LocalDate publishDate;

    @Column(name = "number_of_pages")
    private Integer noOfPages;

    @Column(name = "notes", length = 1000)
    private String notes;

    @Column(name = "file_path", nullable = false)
    private String filePath;

    @Column(name = "created_at")
    private LocalDateTime uploadedAt = LocalDateTime.now();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "uploader_user_id",
            foreignKey = @ForeignKey(name = "FK_documents_users"),
            nullable = true // Make nullable so it can be null if user deleted
    )
    @OnDelete(action = OnDeleteAction.SET_NULL)
    private User uploadedBy;

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
    @JoinTable(
            name = "DocumentTags",
            joinColumns = @JoinColumn(name = "document_id",
                    foreignKey = @ForeignKey(name = "FK_documentTags_documents")),
            inverseJoinColumns = @JoinColumn(name = "tag_id",
                    foreignKey = @ForeignKey(name = "FK_documentTags_tags"))
    )
    private Set<Tag> tags = new HashSet<>();

    @ManyToMany(cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
    @JoinTable(
            name = "DocumentClassifications",
            joinColumns = @JoinColumn(name = "document_id",
                    foreignKey = @ForeignKey(name = "FK_documentClassifications_documents")),
            inverseJoinColumns = @JoinColumn(name = "classification_id",
                    foreignKey = @ForeignKey(name = "FK_documentClassifications_classifications"))
    )
    private Set<Classification> classifications = new HashSet<>();

    //Delete bookmark referenced to doc(when deleted)
    @OneToMany(mappedBy = "document", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Bookmark> bookmarks = new HashSet<>();


    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    public void addTag(Tag tag) {
        tags.add(tag);
        tag.getDocuments().add(this);
    }

    public void removeTag(Tag tag) {
        tags.remove(tag);
        tag.getDocuments().remove(this);
    }

    public void addClassification(Classification classification) {
        classifications.add(classification);
        classification.getDocuments().add(this);
    }

    public void removeClassification(Classification classification) {
        classifications.remove(classification);
        classification.getDocuments().remove(this);
    }
}
