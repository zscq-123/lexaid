package com.lexaid.note;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.lexaid.common.BizException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FavoriteFolderServiceTest {
    private final FavoriteFolderMapper folders = mock(FavoriteFolderMapper.class);
    private final FavoriteMapper favorites = mock(FavoriteMapper.class);
    private final FavoriteFolderService service = new FavoriteFolderService(folders, favorites);

    @Test
    void validatesStoredNameByUnicodeCharacterAfterTrimming() {
        String name = "📚".repeat(40);
        assertThat(service.create(7L, "\u3000" + name + "\u00a0").name()).isEqualTo(name);
        for (String invalid : new String[]{name + "书", "\u3000\t\u00a0", " 未分区 ", "全部分区"}) {
            assertThatThrownBy(() -> service.create(7L, invalid)).isInstanceOf(BizException.class);
        }
    }

    @Test
    void aDuplicateNameReportsConflict() {
        when(folders.insert(any(FavoriteFolder.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertThat(assertThrows(BizException.class, () -> service.create(7L, "案例研习")).getCode())
                .isEqualTo(409);
    }

    @Test
    void anUnavailableDestinationCannotMoveAFavorite() {
        assertThat(assertThrows(BizException.class, () -> service.move(7L, 31L, 64L)).getCode())
                .isEqualTo(404);
        verifyNoInteractions(favorites);
    }

    @Test
    void onlyExplicitNullMeansReturnToUnfiled() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        assertThat(mapper.readValue("{\"folderId\":null}", FavoriteController.MoveRequest.class).folderId()).isNull();
        assertThatThrownBy(() -> mapper.readValue("{}", FavoriteController.MoveRequest.class))
                .isInstanceOf(MismatchedInputException.class);
    }
}
