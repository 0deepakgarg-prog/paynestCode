package com.paynest.documents.service;

import com.paynest.common.ErrorCodes;
import com.paynest.config.security.JWTUtils;
import com.paynest.config.tenant.TenantContext;
import com.paynest.config.tenant.TenantTime;
import com.paynest.documents.dto.DocumentCategoryResponse;
import com.paynest.documents.dto.DocumentDownload;
import com.paynest.documents.dto.DocumentResponse;
import com.paynest.documents.dto.DocumentTypeResponse;
import com.paynest.documents.entity.DocumentCategory;
import com.paynest.documents.entity.DocumentReference;
import com.paynest.documents.entity.DocumentType;
import com.paynest.documents.entity.StoredDocument;
import com.paynest.documents.repository.DocumentCategoryRepository;
import com.paynest.documents.repository.DocumentReferenceRepository;
import com.paynest.documents.repository.DocumentTypeEntityRepository;
import com.paynest.documents.repository.DocumentTypeRepository;
import com.paynest.documents.repository.StoredDocumentRepository;
import com.paynest.exception.ApplicationException;
import com.paynest.users.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentService {

    private static final String ACTIVE = "ACTIVE";
    private static final String DELETED = "DELETED";
    private static final String ADMIN = "ADMIN";
    private static final String CUSTOMER = "CUSTOMER";
    private static final String TRANSACTION = "TRANSACTION";
    private static final String SELFIE = "SELFIE";
    private static final Set<String> ENTITY_TYPES = Set.of(CUSTOMER, "MERCHANT", "AGENT", TRANSACTION);

    private final DocumentCategoryRepository categoryRepository;
    private final DocumentTypeRepository typeRepository;
    private final DocumentTypeEntityRepository typeEntityRepository;
    private final StoredDocumentRepository storedDocumentRepository;
    private final DocumentReferenceRepository referenceRepository;
    private final AccountRepository accountRepository;
    private final DocumentStorageService storageService;
    private final DocumentThumbnailService thumbnailService;

    @Transactional
    public DocumentResponse upload(
            String entityType,
            String entityId,
            String documentTypeCode,
            String documentName,
            MultipartFile file
    ) {
        String resolvedEntityType = normalizeEntityType(entityType);
        String resolvedEntityId = requireValue(entityId, "entityId is required");
        validateAccess(resolvedEntityType, resolvedEntityId);
        validateEntityExists(resolvedEntityType, resolvedEntityId);

        DocumentType type = getActiveType(documentTypeCode);
        if (!typeEntityRepository.existsByIdDocumentTypeIdAndIdEntityType(
                type.getDocumentTypeId(), resolvedEntityType)) {
            throw new ApplicationException(
                    ErrorCodes.DOCUMENT_TYPE_NOT_ALLOWED,
                    "Document type is not allowed for entity type " + resolvedEntityType
            );
        }
        validateUpload(file);

        UUID documentId = UUID.randomUUID();
        String originalFileName = file.getOriginalFilename() == null
                ? "document"
                : file.getOriginalFilename();
        String resolvedDocumentName = documentName == null || documentName.isBlank()
                ? originalFileName
                : documentName.trim();
        String contentType = file.getContentType() == null || file.getContentType().isBlank()
                ? "application/octet-stream"
                : file.getContentType();
        String checksum = checksum(file);
        DocumentCategory category = getCategory(type.getCategoryId());
        DocumentThumbnailService.ThumbnailData thumbnail = shouldGenerateThumbnail(type, contentType)
                ? thumbnailService.generate(file)
                : null;

        Document mongoMetadata = new Document()
                .append("documentId", documentId.toString())
                .append("tenantId", TenantContext.getTenantId())
                .append("entityType", resolvedEntityType)
                .append("entityId", resolvedEntityId)
                .append("categoryCode", category.getCategoryCode())
                .append("documentTypeCode", type.getTypeCode())
                .append("documentName", resolvedDocumentName)
                .append("originalFileName", originalFileName)
                .append("contentType", contentType)
                .append("size", file.getSize())
                .append("checksum", checksum)
                .append("active", true)
                .append("uploadedBy", JWTUtils.getCurrentAccountId())
                .append("uploadedAt", TenantTime.date());

        String gridFsFileId;
        try (InputStream inputStream = file.getInputStream()) {
            gridFsFileId = storageService.store(inputStream, originalFileName, contentType, mongoMetadata);
        } catch (IOException | RuntimeException ex) {
            throw new ApplicationException(
                    ErrorCodes.DOCUMENT_STORAGE_ERROR,
                    "Document upload failed",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }

        String thumbnailGridFsFileId = null;
        try {
            if (thumbnail != null) {
                Document thumbnailMetadata = new Document()
                        .append("documentId", documentId.toString())
                        .append("tenantId", TenantContext.getTenantId())
                        .append("variant", "THUMBNAIL")
                        .append("parentGridFsFileId", gridFsFileId)
                        .append("documentTypeCode", type.getTypeCode());
                thumbnailGridFsFileId = storageService.storeThumbnail(
                        thumbnail.content(),
                        documentId + "-thumbnail.jpg",
                        thumbnailMetadata
                );
                storageService.attachThumbnailMetadata(
                        documentId.toString(),
                        thumbnailGridFsFileId,
                        thumbnail.content().length
                );
            }

            StoredDocument storedDocument = new StoredDocument();
            storedDocument.setDocumentId(documentId);
            storedDocument.setTenantId(TenantContext.getTenantId());
            storedDocument.setDocumentTypeId(type.getDocumentTypeId());
            storedDocument.setDocumentName(resolvedDocumentName);
            storedDocument.setOriginalFileName(originalFileName);
            storedDocument.setContentType(contentType);
            storedDocument.setFileSizeBytes(file.getSize());
            storedDocument.setChecksumSha256(checksum);
            storedDocument.setGridfsBucketName("fs");
            storedDocument.setGridfsFileId(gridFsFileId);
            storedDocument.setThumbnailGridfsFileId(thumbnailGridFsFileId);
            if (thumbnail != null) {
                storedDocument.setThumbnailContentType(thumbnail.contentType());
                storedDocument.setThumbnailSizeBytes((long) thumbnail.content().length);
            }
            storedDocument.setStatus(ACTIVE);
            storedDocument.setUploadedBy(JWTUtils.getCurrentAccountId());
            storedDocumentRepository.save(storedDocument);

            DocumentReference reference = new DocumentReference();
            reference.setDocumentId(documentId);
            reference.setEntityType(resolvedEntityType);
            reference.setEntityId(resolvedEntityId);
            reference.setReferenceRole(TRANSACTION.equals(resolvedEntityType) ? "ATTACHMENT" : "OWNER");
            reference.setIsPrimary(Boolean.FALSE);
            reference.setIsActive(Boolean.TRUE);
            referenceRepository.save(reference);
            storedDocumentRepository.flush();

            return toResponse(storedDocument, reference, type, category);
        } catch (RuntimeException ex) {
            storageService.delete(documentId.toString(), gridFsFileId, thumbnailGridFsFileId);
            throw ex;
        }
    }

    @Transactional(readOnly = true)
    public DocumentResponse getMetadata(UUID documentId) {
        StoredDocument document = getActiveDocument(documentId);
        DocumentReference reference = authorizedReference(documentId);
        DocumentType type = getType(document.getDocumentTypeId());
        return toResponse(document, reference, type, getCategory(type.getCategoryId()));
    }

    @Transactional(readOnly = true)
    public DocumentDownload download(UUID documentId) {
        StoredDocument document = getActiveDocument(documentId);
        authorizedReference(documentId);
        Resource resource = storageService.load(document.getGridfsFileId());
        if (resource == null) {
            throw new ApplicationException(
                    ErrorCodes.DOCUMENT_STORAGE_ERROR,
                    "Stored file is unavailable",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        return new DocumentDownload(
                document.getOriginalFileName(),
                document.getContentType(),
                document.getFileSizeBytes(),
                resource
        );
    }

    @Transactional(readOnly = true)
    public DocumentDownload downloadThumbnail(UUID documentId) {
        StoredDocument document = getActiveDocument(documentId);
        authorizedReference(documentId);
        if (document.getThumbnailGridfsFileId() == null || document.getThumbnailGridfsFileId().isBlank()) {
            throw new ApplicationException(
                    ErrorCodes.DOCUMENT_NOT_FOUND,
                    "Thumbnail is not available for this document",
                    HttpStatus.NOT_FOUND
            );
        }
        Resource resource = storageService.load(document.getThumbnailGridfsFileId());
        if (resource == null) {
            throw new ApplicationException(
                    ErrorCodes.DOCUMENT_STORAGE_ERROR,
                    "Stored thumbnail is unavailable",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        return new DocumentDownload(
                document.getDocumentId() + "-thumbnail.jpg",
                document.getThumbnailContentType(),
                document.getThumbnailSizeBytes(),
                resource
        );
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listByEntity(String entityType, String entityId) {
        String resolvedType = normalizeEntityType(entityType);
        String resolvedId = requireValue(entityId, "entityId is required");
        validateAccess(resolvedType, resolvedId);
        return toResponses(referenceRepository.findByEntityTypeAndEntityIdAndIsActiveTrueOrderByCreatedAtDesc(
                resolvedType, resolvedId));
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> listForAccount(String accountId) {
        String resolvedId = requireValue(accountId, "accountId is required");
        validateAccountAccess(resolvedId);
        return toResponses(referenceRepository.findByEntityIdAndIsActiveTrueOrderByCreatedAtDesc(resolvedId)
                .stream()
                .filter(reference -> !TRANSACTION.equals(reference.getEntityType()))
                .filter(reference -> isAdmin() || currentEntityType().equals(reference.getEntityType()))
                .toList());
    }

    @Transactional
    public void delete(UUID documentId) {
        StoredDocument document = getActiveDocument(documentId);
        authorizedReference(documentId);
        document.setStatus(DELETED);
        document.setDeletedAt(TenantTime.now());
        storedDocumentRepository.save(document);
        List<DocumentReference> references = referenceRepository.findByDocumentIdAndIsActiveTrue(documentId);
        references.forEach(reference -> reference.setIsActive(Boolean.FALSE));
        referenceRepository.saveAll(references);
        storedDocumentRepository.flush();
        try {
            storageService.delete(
                    documentId.toString(),
                    document.getGridfsFileId(),
                    document.getThumbnailGridfsFileId()
            );
        } catch (RuntimeException ex) {
            throw new ApplicationException(
                    ErrorCodes.DOCUMENT_STORAGE_ERROR,
                    "Document metadata was deleted but GridFS cleanup failed",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    @Transactional(readOnly = true)
    public List<DocumentCategoryResponse> listCategories() {
        return categoryRepository.findByIsActiveTrueOrderByCategoryNameAsc().stream()
                .map(category -> new DocumentCategoryResponse(
                        category.getCategoryCode(), category.getCategoryName(), category.getDescription()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DocumentTypeResponse> listTypes(String entityType, String categoryCode) {
        String resolvedEntityType = entityType == null || entityType.isBlank()
                ? null
                : normalizeEntityType(entityType);
        if (resolvedEntityType != null) {
            validateCatalogueAccess(resolvedEntityType);
        }

        List<DocumentType> types;
        if (categoryCode == null || categoryCode.isBlank()) {
            types = typeRepository.findByIsActiveTrueOrderByTypeNameAsc();
        } else {
            DocumentCategory category = categoryRepository.findByCategoryCodeAndIsActiveTrue(
                            categoryCode.trim().toUpperCase(Locale.ROOT))
                    .orElseThrow(() -> new ApplicationException(
                            ErrorCodes.DOCUMENT_TYPE_NOT_FOUND, "Document category not found"));
            types = typeRepository.findByCategoryIdAndIsActiveTrueOrderByTypeNameAsc(category.getCategoryId());
        }

        return types.stream()
                .filter(type -> resolvedEntityType == null
                        || typeEntityRepository.existsByIdDocumentTypeIdAndIdEntityType(
                        type.getDocumentTypeId(), resolvedEntityType))
                .map(type -> {
                    DocumentCategory category = getCategory(type.getCategoryId());
                    List<String> entityTypes = typeEntityRepository.findAll().stream()
                            .filter(mapping -> mapping.getId().getDocumentTypeId().equals(type.getDocumentTypeId()))
                            .map(mapping -> mapping.getId().getEntityType())
                            .sorted()
                            .toList();
                    return new DocumentTypeResponse(
                            category.getCategoryCode(),
                            type.getTypeCode(),
                            type.getTypeName(),
                            Boolean.TRUE.equals(type.getMultipleAllowed()),
                            Boolean.TRUE.equals(type.getVerificationRequired()),
                            entityTypes
                    );
                })
                .toList();
    }

    private List<DocumentResponse> toResponses(List<DocumentReference> references) {
        List<DocumentResponse> responses = new ArrayList<>();
        for (DocumentReference reference : references) {
            storedDocumentRepository.findByDocumentIdAndStatus(reference.getDocumentId(), ACTIVE)
                    .ifPresent(document -> {
                        DocumentType type = getType(document.getDocumentTypeId());
                        responses.add(toResponse(document, reference, type, getCategory(type.getCategoryId())));
                    });
        }
        return responses;
    }

    private DocumentResponse toResponse(
            StoredDocument document,
            DocumentReference reference,
            DocumentType type,
            DocumentCategory category
    ) {
        return new DocumentResponse(
                document.getDocumentId(),
                reference.getEntityType(),
                reference.getEntityId(),
                category.getCategoryCode(),
                type.getTypeCode(),
                type.getTypeName(),
                document.getDocumentName(),
                document.getOriginalFileName(),
                document.getContentType(),
                document.getFileSizeBytes(),
                document.getChecksumSha256(),
                document.getThumbnailGridfsFileId() != null,
                document.getStatus(),
                document.getUploadedBy(),
                document.getUploadedAt()
        );
    }

    private StoredDocument getActiveDocument(UUID documentId) {
        return storedDocumentRepository.findByDocumentIdAndStatus(documentId, ACTIVE)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.DOCUMENT_NOT_FOUND, "Document not found", HttpStatus.NOT_FOUND));
    }

    private DocumentReference authorizedReference(UUID documentId) {
        return referenceRepository.findByDocumentIdAndIsActiveTrue(documentId).stream()
                .filter(reference -> canAccess(reference.getEntityType(), reference.getEntityId()))
                .findFirst()
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.INVALID_PRIVILEGES,
                        "Token does not have necessary access",
                        HttpStatus.FORBIDDEN
                ));
    }

    private void validateAccess(String entityType, String entityId) {
        if (!canAccess(entityType, entityId)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_PRIVILEGES,
                    "Token does not have necessary access",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private boolean canAccess(String entityType, String entityId) {
        if (isAdmin()) {
            return true;
        }
        return !TRANSACTION.equals(entityType)
                && JWTUtils.getCurrentAccountId().equalsIgnoreCase(entityId)
                && currentEntityType().equals(entityType);
    }

    private void validateAccountAccess(String accountId) {
        if (!isAdmin() && !JWTUtils.getCurrentAccountId().equalsIgnoreCase(accountId)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_PRIVILEGES,
                    "Token does not have necessary access",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void validateCatalogueAccess(String entityType) {
        if (!isAdmin() && !currentEntityType().equals(entityType)) {
            throw new ApplicationException(
                    ErrorCodes.INVALID_PRIVILEGES,
                    "Token does not have necessary access",
                    HttpStatus.FORBIDDEN
            );
        }
    }

    private void validateEntityExists(String entityType, String entityId) {
        if (!TRANSACTION.equals(entityType) && !accountRepository.existsById(entityId)) {
            throw new ApplicationException(ErrorCodes.ACCOUNT_NOT_FOUND, "Account not found", HttpStatus.NOT_FOUND);
        }
    }

    private String currentEntityType() {
        String accountType = JWTUtils.getCurrentAccountType().toUpperCase(Locale.ROOT);
        return "SUBSCRIBER".equals(accountType) ? CUSTOMER : accountType;
    }

    private boolean isAdmin() {
        return ADMIN.equalsIgnoreCase(JWTUtils.getCurrentAccountType());
    }

    private String normalizeEntityType(String entityType) {
        String normalized = requireValue(entityType, "entityType is required").toUpperCase(Locale.ROOT);
        if (!ENTITY_TYPES.contains(normalized)) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Unsupported entityType");
        }
        return normalized;
    }

    private DocumentType getActiveType(String typeCode) {
        String normalized = requireValue(typeCode, "documentTypeCode is required").toUpperCase(Locale.ROOT);
        return typeRepository.findByTypeCodeAndIsActiveTrue(normalized)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.DOCUMENT_TYPE_NOT_FOUND, "Document type not found", HttpStatus.NOT_FOUND));
    }

    private DocumentType getType(Long typeId) {
        return typeRepository.findById(typeId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.DOCUMENT_TYPE_NOT_FOUND, "Document type not found", HttpStatus.NOT_FOUND));
    }

    private DocumentCategory getCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ApplicationException(
                        ErrorCodes.DOCUMENT_TYPE_NOT_FOUND, "Document category not found", HttpStatus.NOT_FOUND));
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, "Document file is required");
        }
    }

    private boolean shouldGenerateThumbnail(DocumentType type, String contentType) {
        return contentType.toLowerCase(Locale.ROOT).startsWith("image/")
                && (SELFIE.equals(type.getTypeCode()) || Boolean.TRUE.equals(type.getVerificationRequired()));
    }

    private String checksum(MultipartFile file) {
        try (DigestInputStream stream = new DigestInputStream(
                file.getInputStream(), MessageDigest.getInstance("SHA-256"))) {
            stream.transferTo(java.io.OutputStream.nullOutputStream());
            return HexFormat.of().formatHex(stream.getMessageDigest().digest());
        } catch (IOException | NoSuchAlgorithmException ex) {
            throw new ApplicationException(
                    ErrorCodes.DOCUMENT_STORAGE_ERROR,
                    "Unable to calculate document checksum",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private String requireValue(String value, String error) {
        if (value == null || value.isBlank()) {
            throw new ApplicationException(ErrorCodes.INVALID_REQUEST, error);
        }
        return value.trim();
    }
}
