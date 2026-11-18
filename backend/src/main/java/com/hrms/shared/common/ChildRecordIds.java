package com.hrms.shared.common;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ChildRecordIds {
    private ChildRecordIds() {
    }

    public static <T> void validate(List<T> incoming, List<T> existing, Function<T, Long> id) {
        if (incoming == null || incoming.stream().anyMatch(java.util.Objects::isNull)) {
            throw ApiException.badRequest("Provide a valid list of child records");
        }
        Set<Long> allowed = existing.stream().map(id).collect(Collectors.toCollection(HashSet::new));
        for (T item : incoming) {
            Long requested = id.apply(item);
            if (requested != null && !allowed.remove(requested)) {
                throw ApiException.badRequest("A child record is duplicated or does not belong to this record");
            }
        }
    }
}
