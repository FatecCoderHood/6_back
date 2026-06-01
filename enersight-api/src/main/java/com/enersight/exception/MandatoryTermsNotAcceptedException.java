package com.enersight.exception;

import java.util.List;
import java.util.UUID;

public class MandatoryTermsNotAcceptedException extends RuntimeException {

    private final List<UUID> missingTermIds;

    public MandatoryTermsNotAcceptedException(List<UUID> missingTermIds) {
        super("Missing acceptance for mandatory terms: " + missingTermIds);
        this.missingTermIds = missingTermIds;
    }

    public List<UUID> getMissingTermIds() {
        return missingTermIds;
    }
}
