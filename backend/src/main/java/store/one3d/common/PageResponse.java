package store.one3d.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/** One page of a list, newest first unless stated otherwise. */
public record PageResponse<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static final int MAX_SIZE = 50;

    public static <E, T> PageResponse<T> of(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    /** Clamps client paging input to 0+ and 1..{@value #MAX_SIZE}. */
    public static int page(int requested) {
        return Math.max(requested, 0);
    }

    public static int size(int requested) {
        return Math.min(Math.max(requested, 1), MAX_SIZE);
    }
}
