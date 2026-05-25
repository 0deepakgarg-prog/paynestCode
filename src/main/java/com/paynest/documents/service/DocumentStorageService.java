package com.paynest.documents.service;

import com.mongodb.client.gridfs.model.GridFSFile;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class DocumentStorageService {

    private static final String DOCUMENTS_COLLECTION = "documents";

    private final GridFsTemplate gridFsTemplate;
    private final MongoTemplate mongoTemplate;

    public String store(InputStream content, String fileName, String contentType, Document metadata) {
        ObjectId gridFsFileId = gridFsTemplate.store(content, fileName, contentType, metadata);
        try {
            metadata.put("_id", metadata.getString("documentId"));
            metadata.put("gridFsFileId", gridFsFileId);
            mongoTemplate.save(metadata, DOCUMENTS_COLLECTION);
        } catch (RuntimeException ex) {
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(gridFsFileId)));
            throw ex;
        }
        return gridFsFileId.toHexString();
    }

    public Resource load(String gridFsFileId) {
        GridFSFile file = gridFsTemplate.findOne(
                Query.query(Criteria.where("_id").is(new ObjectId(gridFsFileId)))
        );
        return file == null ? null : gridFsTemplate.getResource(file);
    }

    public String storeThumbnail(byte[] content, String fileName, Document metadata) {
        ObjectId gridFsFileId = gridFsTemplate.store(
                new ByteArrayInputStream(content),
                fileName,
                "image/jpeg",
                metadata
        );
        return gridFsFileId.toHexString();
    }

    public void attachThumbnailMetadata(String documentId, String thumbnailGridFsFileId, long size) {
        Document metadata = mongoTemplate.findOne(
                Query.query(Criteria.where("_id").is(documentId)),
                Document.class,
                DOCUMENTS_COLLECTION
        );
        if (metadata == null) {
            throw new IllegalStateException("Mongo document metadata is unavailable");
        }
        metadata.put("thumbnailGridFsFileId", new ObjectId(thumbnailGridFsFileId));
        metadata.put("thumbnailContentType", "image/jpeg");
        metadata.put("thumbnailSize", size);
        mongoTemplate.save(metadata, DOCUMENTS_COLLECTION);
    }

    public void delete(String documentId, String gridFsFileId, String thumbnailGridFsFileId) {
        mongoTemplate.remove(
                Query.query(Criteria.where("_id").is(documentId)),
                DOCUMENTS_COLLECTION
        );
        gridFsTemplate.delete(Query.query(Criteria.where("_id").is(new ObjectId(gridFsFileId))));
        if (thumbnailGridFsFileId != null && !thumbnailGridFsFileId.isBlank()) {
            gridFsTemplate.delete(Query.query(Criteria.where("_id").is(new ObjectId(thumbnailGridFsFileId))));
        }
    }
}
