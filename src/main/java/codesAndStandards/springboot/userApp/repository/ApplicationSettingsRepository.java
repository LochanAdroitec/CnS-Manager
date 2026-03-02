package codesAndStandards.springboot.userApp.repository;

import codesAndStandards.springboot.userApp.entity.ApplicationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ApplicationSettingsRepository extends JpaRepository<ApplicationSettings, Long> {

    /**
     * Find a setting by its name
     */
    Optional<ApplicationSettings> findBySettingName(String settingName);

    /**
     * Check if a setting exists by name
     */
    boolean existsBySettingName(String settingName);

    /**
     * Get setting value by name (returns value directly)
     */
    @Query("SELECT s.settingValue FROM ApplicationSettings s WHERE s.settingName = :name")
    String getSettingValue(@Param("name") String settingName);
}
