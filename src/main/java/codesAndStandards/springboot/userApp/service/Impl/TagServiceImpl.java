package codesAndStandards.springboot.userApp.service.Impl;

import codesAndStandards.springboot.userApp.dto.TagDto;
import codesAndStandards.springboot.userApp.entity.Tag;
import codesAndStandards.springboot.userApp.repository.TagRepository;
import codesAndStandards.springboot.userApp.service.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class TagServiceImpl implements TagService {

    @Autowired
    private TagRepository tagRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TagDto> getAllTags() {
        return tagRepository.findAll()
                .stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public TagDto getTagById(Long id) {
        Tag tag = tagRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Tag not found"));
        return convertToDto(tag);
    }

    @Override
    @Transactional
    public TagDto createTag(String tagName) {
        if (tagRepository.existsByTagName(tagName)) {
            throw new RuntimeException("Tag already exists");
        }

        Tag tag = new Tag();
        tag.setTagName(tagName);
        tag = tagRepository.save(tag);

        return convertToDto(tag);
    }

    @Override
    @Transactional
    public TagDto findOrCreateTag(String tagName) {
        return tagRepository.findByTagName(tagName)
                .map(this::convertToDto)
                .orElseGet(() -> createTag(tagName));
    }

    @Override
    @Transactional
    public void deleteTag(Long id) {
        tagRepository.deleteById(id);
    }

    private TagDto convertToDto(Tag tag) {
        TagDto dto = new TagDto();
        dto.setId(tag.getId());
        dto.setTagName(tag.getTagName());
        return dto;
    }
}