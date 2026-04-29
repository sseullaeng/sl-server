package com.sseulang.domain.item.domain;

import com.sseulang.global.common.BaseEntity;
import com.sseulang.global.exception.BusinessException;
import com.sseulang.global.exception.ErrorCode;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Item Aggregate Root. V1 스키마 {@code items} 매핑.
 *
 * <p>자식 Entity {@link ItemImage} 는 본 Root 메서드를 통해서만 추가/제거. 외부에서 컬렉션
 * 직접 조작 금지({@link #getImages()} 는 unmodifiable view).</p>
 *
 * <p>거래 진행 상태(예약/거래완료) 전이는 transaction 도메인이 트리거하며, 본 Aggregate 는
 * 판매자 본인이 직접 호출 가능한 {@link #updateInfo}, {@link #markAsHidden}, {@link #markAsDeleted},
 * {@link #restore} 만 노출한다.</p>
 */
@Entity
@Table(name = "items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Item extends BaseEntity {

    private static final int MAX_IMAGES = 5;
    private static final int TITLE_MAX_LENGTH = 200;
    private static final int REGION_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "seller_id", nullable = false)
    private Long sellerId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "price", nullable = false)
    private long price;

    @Column(name = "deposit")
    private Long deposit;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_unit", length = 20)
    private RentalUnit rentalUnit;

    @Enumerated(EnumType.STRING)
    @Column(name = "trade_type", nullable = false)
    private TradeType tradeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ItemStatus status;

    @Column(name = "region", length = REGION_MAX_LENGTH)
    private String region;

    @Column(name = "view_count", nullable = false)
    private int viewCount;

    @Column(name = "wishlist_count", nullable = false)
    private int wishlistCount;

    @OneToMany(mappedBy = "item", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC")
    private final List<ItemImage> images = new ArrayList<>();

    public static Item create(
            Long sellerId,
            Long categoryId,
            String title,
            String description,
            long price,
            Long deposit,
            RentalUnit rentalUnit,
            TradeType tradeType,
            String region
    ) {
        if (sellerId == null || sellerId <= 0) {
            throw new IllegalArgumentException("sellerId 는 양수여야 합니다");
        }
        if (tradeType == null) {
            throw new IllegalArgumentException("tradeType 은 필수입니다");
        }
        validateTitle(title);
        validateDescription(description);
        if (price < 0) {
            throw new IllegalArgumentException("price 는 0 이상이어야 합니다");
        }
        validateRentalFields(tradeType, deposit, rentalUnit);
        validateRegion(region);

        Item item = new Item();
        item.sellerId = sellerId;
        item.categoryId = categoryId;
        item.title = title;
        item.description = description;
        item.price = price;
        item.deposit = deposit;
        item.rentalUnit = rentalUnit;
        item.tradeType = tradeType;
        item.region = region;
        item.status = ItemStatus.판매중;
        item.viewCount = 0;
        item.wishlistCount = 0;
        return item;
    }

    public void addImage(String imageUrl, int sortOrder, boolean thumbnail) {
        if (images.size() >= MAX_IMAGES) {
            throw new BusinessException(ErrorCode.ITEM_IMAGE_LIMIT_EXCEEDED);
        }
        images.add(new ItemImage(this, imageUrl, sortOrder, thumbnail));
    }

    public void clearImages() {
        images.clear();
    }

    /** 외부에 노출되는 이미지 컬렉션은 immutable. 변경은 {@link #addImage} / {@link #clearImages}. */
    public List<ItemImage> getImages() {
        return Collections.unmodifiableList(images);
    }

    public void updateInfo(
            String title,
            String description,
            long price,
            Long deposit,
            RentalUnit rentalUnit,
            String region
    ) {
        if (!status.isEditable()) {
            throw new IllegalStateException("현재 상태(" + status + ")에서는 수정할 수 없습니다");
        }
        validateTitle(title);
        validateDescription(description);
        if (price < 0) {
            throw new IllegalArgumentException("price 는 0 이상이어야 합니다");
        }
        validateRentalFields(tradeType, deposit, rentalUnit);
        validateRegion(region);

        this.title = title;
        this.description = description;
        this.price = price;
        this.deposit = deposit;
        this.rentalUnit = rentalUnit;
        this.region = region;
    }

    public void assignCategory(Long categoryId) {
        this.categoryId = categoryId;
    }

    public void markAsHidden() {
        if (status == ItemStatus.삭제) {
            throw new IllegalStateException("삭제된 물품은 비공개로 전환할 수 없습니다");
        }
        this.status = ItemStatus.비공개;
    }

    public void markAsDeleted() {
        this.status = ItemStatus.삭제;
    }

    public void restore() {
        if (status != ItemStatus.비공개) {
            throw new IllegalStateException("비공개 상태에서만 복구할 수 있습니다");
        }
        this.status = ItemStatus.판매중;
    }

    public void incrementViewCount() {
        this.viewCount++;
    }

    public boolean isOwnedBy(Long userId) {
        return userId != null && userId.equals(this.sellerId);
    }

    private static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title 은 비어있을 수 없습니다");
        }
        if (title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("title 은 " + TITLE_MAX_LENGTH + "자를 초과할 수 없습니다");
        }
    }

    private static void validateDescription(String description) {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description 은 비어있을 수 없습니다");
        }
    }

    private static void validateRegion(String region) {
        if (region != null && region.length() > REGION_MAX_LENGTH) {
            throw new IllegalArgumentException("region 은 " + REGION_MAX_LENGTH + "자를 초과할 수 없습니다");
        }
    }

    private static void validateRentalFields(TradeType tradeType, Long deposit, RentalUnit rentalUnit) {
        if (tradeType.requiresDeposit()) {
            if (deposit == null || deposit < 0) {
                throw new IllegalArgumentException("대여 거래는 0 이상의 deposit 이 필수입니다");
            }
            if (rentalUnit == null) {
                throw new IllegalArgumentException("대여 거래는 rentalUnit 이 필수입니다");
            }
        } else {
            if (deposit != null) {
                throw new IllegalArgumentException("대여 외 거래에는 deposit 을 지정할 수 없습니다");
            }
            if (rentalUnit != null) {
                throw new IllegalArgumentException("대여 외 거래에는 rentalUnit 을 지정할 수 없습니다");
            }
        }
    }
}
