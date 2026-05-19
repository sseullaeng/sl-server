package com.sseulang.domain.category.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryTest {

    @Test
    @DisplayName("createRoot 정상_parentId null 이고 active=true")
    void createRoot_정상() {
        Category c = Category.createRoot("디지털/가전", 1);

        assertThat(c.getParentId()).isNull();
        assertThat(c.getName()).isEqualTo("디지털/가전");
        assertThat(c.getSortOrder()).isEqualTo(1);
        assertThat(c.isActive()).isTrue();
        assertThat(c.isRoot()).isTrue();
    }

    @Test
    @DisplayName("createChild 정상_parentId 가 박힘")
    void createChild_정상() {
        Category c = Category.createChild(7L, "스마트폰", 1);

        assertThat(c.getParentId()).isEqualTo(7L);
        assertThat(c.getName()).isEqualTo("스마트폰");
        assertThat(c.isRoot()).isFalse();
    }

    @Test
    @DisplayName("createRoot 빈 name_거부")
    void createRoot_빈_name_거부() {
        assertThatThrownBy(() -> Category.createRoot("", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Category.createRoot(null, 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Category.createRoot("   ", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createRoot name 50자 초과_거부")
    void createRoot_name_길이초과_거부() {
        String tooLong = "가".repeat(51);
        assertThatThrownBy(() -> Category.createRoot(tooLong, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createRoot 음수 sortOrder_거부")
    void createRoot_음수_sortOrder_거부() {
        assertThatThrownBy(() -> Category.createRoot("name", -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createChild null parentId_거부")
    void createChild_null_parentId_거부() {
        assertThatThrownBy(() -> Category.createChild(null, "스마트폰", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("createChild 0 이하 parentId_거부")
    void createChild_비양수_parentId_거부() {
        assertThatThrownBy(() -> Category.createChild(0L, "name", 1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Category.createChild(-1L, "name", 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
