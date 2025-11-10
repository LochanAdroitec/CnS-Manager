package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.dto.DocumentDto;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DocumentService {
    void saveDocument(DocumentDto documentDto, MultipartFile file, String username) throws Exception;
    List<DocumentDto> findAllDocuments();
//    List<DocumentDto> findDocumentsByUsername();
    DocumentDto findDocumentById(Long id);
//    For editing the Document
    void updateDocument(Long id, DocumentDto documentDto,MultipartFile file, String username) throws Exception;
    void deleteDocument(Long id);
    String getFilePath(Long id);
}