package com.sseulang.domain.category.application;

import com.sseulang.domain.category.application.dto.CategoryNodeResult;
import com.sseulang.domain.category.application.dto.CategoryResult;
import com.sseulang.domain.category.domain.Category;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryApplicationServiceTest {

    private InMemoryFakeCategoryRepository repo;
    private CategoryApplicationService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryFakeCategoryRepository();
        service = new CategoryApplicationService(repo);
    }

    @Test
    @DisplayName("getActiveTree 빈 저장소_빈 리스트")
    void getActiveTree_빈_저장소_빈_리스트() {
        assertThat(service.getActiveTree()).isEmpty();
    }

    @Test
    @DisplayName("getActiveTree 루트만_자식 없음")
    void getActiveTree_루트만() {
        Category digital = repo.insert(Category.createRoot("디지털/가전", 1));
        Category furniture = repo.insert(Category.createRoot("가구/인테리어", 2));

        List<CategoryNodeResult> tree = service.getActiveTree();

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).name()).isEqualTo("디지털/가전");
        assertThat(tree.get(0).children()).isEmpty();
        assertThat(tree.get(1).name()).isEqualTo("가구/인테리어");
        assertThat(tree.get(0).id()).isEqualTo(digital.getId());
        assertThat(tree.get(1).id()).isEqualTo(furniture.getId());
    }

    @Test
    @DisplayName("getActiveTree 루트+2차_자식이 sortOrder 순으로 묶임")
    void getActiveTree_2단_트리() {
        Category digital = repo.insert(Category.createRoot("디지털/가전", 1));
        repo.insert(Category.createChild(digital.getId(), "노트북", 3));
        repo.insert(Category.createChild(digital.getId(), "스마트폰", 1));
        repo.insert(Category.createChild(digital.getId(), "태블릿/PC", 2));

        List<CategoryNodeResult> tree = service.getActiveTree();

        assertThat(tree).hasSize(1);
        CategoryNodeResult root = tree.get(0);
        assertThat(root.children())
                .extracting(CategoryNodeResult::name)
                .containsExactly("스마트폰", "태블릿/PC", "노트북");
    }

    @Test
    @DisplayName("getActiveTree 루트끼리도 sortOrder 순")
    void getActiveTree_루트_정렬() {
        repo.insert(Category.createRoot("기타", 99));
        repo.insert(Category.createRoot("디지털/가전", 1));
        repo.insert(Category.createRoot("가구/인테리어", 2));

        List<CategoryNodeResult> tree = service.getActiveTree();

        assertThat(tree)
                .extracting(CategoryNodeResult::name)
                .containsExactly("디지털/가전", "가구/인테리어", "기타");
    }

    @Test
    @DisplayName("getById 정상")
    void getById_정상() {
        Category root = repo.insert(Category.createRoot("디지털/가전", 1));
        Category child = repo.insert(Category.createChild(root.getId(), "스마트폰", 1));

        CategoryResult r = service.getById(child.getId());

        assertThat(r.id()).isEqualTo(child.getId());
        assertThat(r.parentId()).isEqualTo(root.getId());
        assertThat(r.name()).isEqualTo("스마트폰");
        assertThat(r.sortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("getById 없는 id_CATEGORY_NOT_FOUND")
    void getById_없는_id() {
        assertThatThrownBy(() -> service.getById(9999L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.CATEGORY_NOT_FOUND);
    }
}
