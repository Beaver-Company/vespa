// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include <vespa/searchcore/proton/server/documentdbconfig.h>

namespace proton {
namespace test {

/**
 * Builder for instances of DocumentDBConfig used in unit tests.
 */
class DocumentDBConfigBuilder {
private:
    int64_t _generation;
    DocumentDBConfig::RankProfilesConfigSP _rankProfiles;
    DocumentDBConfig::RankingConstants::SP _rankingConstants;
    DocumentDBConfig::IndexschemaConfigSP _indexschema;
    DocumentDBConfig::AttributesConfigSP _attributes;
    DocumentDBConfig::SummaryConfigSP _summary;
    DocumentDBConfig::SummarymapConfigSP _summarymap;
    DocumentDBConfig::JuniperrcConfigSP _juniperrc;
    DocumentDBConfig::DocumenttypesConfigSP _documenttypes;
    document::DocumentTypeRepo::SP _repo;
    DocumentDBConfig::ImportedFieldsConfigSP _importedFields;
    search::TuneFileDocumentDB::SP _tuneFileDocumentDB;
    search::index::Schema::SP _schema;
    DocumentDBConfig::MaintenanceConfigSP _maintenance;
    vespalib::string _configId;
    vespalib::string _docTypeName;
    config::ConfigSnapshot _extraConfig;

public:
    DocumentDBConfigBuilder(int64_t generation,
                            const search::index::Schema::SP &schema,
                            const vespalib::string &configId,
                            const vespalib::string &docTypeName);

    DocumentDBConfigBuilder &repo(const document::DocumentTypeRepo::SP &repo_in) {
        _repo = repo_in;
        return *this;
    }
    DocumentDBConfigBuilder &rankProfiles(const DocumentDBConfig::RankProfilesConfigSP &rankProfiles_in) {
        _rankProfiles = rankProfiles_in;
        return *this;
    }
    DocumentDBConfigBuilder &attributes(const DocumentDBConfig::AttributesConfigSP &attributes_in) {
        _attributes = attributes_in;
        return *this;
    }
    DocumentDBConfig::SP build();
};

}
}
