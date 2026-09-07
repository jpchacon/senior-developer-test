package com.caseware.templateupdate.config;

import com.caseware.templateupdate.api.TemplateDiffSource;
import com.caseware.templateupdate.domain.ChangeKind;
import com.caseware.templateupdate.domain.JsonDiff;
import com.caseware.templateupdate.domain.JsonDiffEntry;
import com.caseware.templateupdate.domain.TemplateId;
import com.caseware.templateupdate.domain.TemplateVersion;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Stands in for the Product Template Store's diff facility.
 *
 * <p>The spec grants a fast, reliable diff between two template versions and puts that system
 * outside this exercise, so this implementation fabricates a plausible diff whose size scales with
 * the version gap. It exists so the read path can be exercised end to end; the real implementation
 * calls the template store and nothing else about the design changes.
 */
@Component
public class SampleTemplateDiffSource implements TemplateDiffSource {

    /** Creates the sample source. */
    public SampleTemplateDiffSource() {
        // Nothing to configure.
    }

    @Override
    public JsonDiff diff(TemplateId templateId, TemplateVersion fromVersion, TemplateVersion toVersion) {
        List<JsonDiffEntry> entries = new ArrayList<>();
        for (int version = fromVersion.number() + 1; version <= toVersion.number(); version++) {
            entries.add(
                    JsonDiffEntry.builder()
                            .pointer("/audit_procedures/" + version + "/title")
                            .kind(ChangeKind.MODIFIED)
                            .before("Wording as published in v" + (version - 1))
                            .after("Wording as published in v" + version)
                            .build());
            entries.add(
                    JsonDiffEntry.builder()
                            .pointer("/disclosures/" + version + "/guidance")
                            .kind(ChangeKind.ADDED)
                            .after("Guidance introduced in v" + version)
                            .build());
        }
        return new JsonDiff(entries);
    }
}
