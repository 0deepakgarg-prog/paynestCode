package com.paynest.documents.entity;

import com.paynest.config.tenant.TenantTime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stored_document")
@Getter
@Setter
public class StoredDocument {

    @Id
    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "document_type_id", nullable = false)
    private Long documentTypeId;

    @Column(name = "document_name", nullable = false, length = 255)
    private String documentName;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "content_type", nullable = false, length = 150)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(name = "gridfs_bucket_name", nullable = false, length = 100)
    private String gridfsBucketName;

    @Column(name = "gridfs_file_id", nullable = false, length = 64)
    private String gridfsFileId;

    @Column(name = "thumbnail_gridfs_file_id", length = 64)
    private String thumbnailGridfsFileId;

    @Column(name = "thumbnail_content_type", length = 150)
    private String thumbnailContentType;

    @Column(name = "thumbnail_size_bytes")
    private Long thumbnailSizeBytes;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    @Column(name = "uploaded_by", nullable = false, length = 100)
    private String uploadedBy;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = TenantTime.now();
        if (uploadedAt == null) {
            uploadedAt = now;
        }
        updatedAt = now;
        if (status == null) {
            status = "ACTIVE";
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = TenantTime.now();
    }
}
