package codesAndStandards.springboot.userApp.service;

import codesAndStandards.springboot.userApp.dto.TagDto;

import java.util.List;

public interface TagService {
    List<TagDto> getAllTags();
    TagDto getTagById(Long id);
    TagDto createTag(String tagName);
    TagDto findOrCreateTag(String tagName);
    void deleteTag(Long id);
}