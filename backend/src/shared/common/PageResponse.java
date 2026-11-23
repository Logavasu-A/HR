package shared.common;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/** Standardized pagination payload returned by list endpoints when a page parameter is supplied. */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 200;

    public static Pageable pageable(int page, int size, Sort sort) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, MAX_SIZE));
        return sort == null || sort.isUnsorted()
                ? PageRequest.of(safePage, safeSize)
                : PageRequest.of(safePage, safeSize, sort);
    }

    public static Pageable pageable(int page, int size) {
        return pageable(page, size, Sort.unsorted());
    }

    public static <T> PageResponse<T> of(Page<T> p) {
        return new PageResponse<>(
                p.getContent(),
                p.getNumber(),
                p.getSize(),
                p.getTotalElements(),
                p.getTotalPages(),
                p.isFirst(),
                p.isLast());
    }
}
