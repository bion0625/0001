package com.uj.stxtory.domain.dto.deal;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Slf4j
public abstract class DealInfo {
    public List<DealItem> deleteItems = new ArrayList<>();// 업데이트 작업 후 매도 종목
    public List<DealItem> updateItems = new ArrayList<>();// 업데이트 작업 후 갱신 종목

    public abstract List<DealItem> getAll();
    public abstract int getPage();
    public abstract List<DealPrice> getPrice(DealItem item, int page);
    public abstract List<DealPrice> getPriceByPage(DealItem item, int from, int to);

    public abstract boolean CustomCheck(DealItem item);

    public List<DealItem> calculateByThreeDaysByPageForSave() {
        log.info("save log start!");

        return getAll().parallelStream()
                .filter(item -> {
                    List<DealPrice> prices = getPrice(item, 1);

                    // 거래량이 0이면 하루 전으로 계산
                    int lastdayIndex = prices.get(0).getVolume() == 0 ? 1 : 0;

                    // 최근 3일 중 마지막일이 최고 거래량이면 제외
                    DealPrice checkPrice = prices.subList(lastdayIndex, (lastdayIndex + 3)).parallelStream().reduce((p, c) -> p.getVolume() > c.getVolume() ? p : c).orElse(null);
                    if (checkPrice != null && prices.get(0).getVolume() == checkPrice.getVolume()) return false;

                    // 마지막일 diff가 5% ~ 15% 내에 있지 않으면 제외
                    double diffPercent = (double) (prices.get(lastdayIndex).getDiff() * 100) / (double) prices.get(lastdayIndex + 1).getClose();
                    if (prices.get(lastdayIndex).getDiff() < 0 || (diffPercent < 5 || diffPercent > 15)) return false;

                    // 3일 연달아 가격 상승이 아니면 제외
                    if(prices.get(lastdayIndex).getHigh() <= prices.get(lastdayIndex + 1).getHigh() || prices.get(lastdayIndex + 1).getHigh() <= prices.get(lastdayIndex + 2).getHigh()
                            || prices.get(lastdayIndex).getLow() <= prices.get(lastdayIndex + 1).getLow() || prices.get(lastdayIndex + 1).getLow() <= prices.get(lastdayIndex + 2).getLow()) {
                        return false;
                    }

                    // 고점 대비 5% 미만이면 제외 - 현재가(종가) 기준
                    if (prices.get(lastdayIndex).getClose() < (Math.round(prices.get(lastdayIndex).getHigh() * 0.95))) return false;

                    // 부하를 방지하기 위해 신고가 설정할 때 다시 구하기
                    prices = getPriceByPage(item, 1, getPage());

                    // 조회 기간(6개월) 중 신고가가 아니면 제외
                    checkPrice = prices.parallelStream().max(Comparator.comparingLong(DealPrice::getHigh)).orElse(null);
                    return checkPrice != null && prices.get(lastdayIndex).getHigh() == checkPrice.getHigh();
                })
                // 로그
                .peek(item -> log.info(String.format("\tsuccess:\t%s", item.getName())))
                .collect(Collectors.toList());
    }

    public void calculateForTodayUpdate(List<DealItem> savedItem) {
        savedItem.parallelStream()
                .filter(item -> {
                    if (!CustomCheck(item)) {
                        deleteItems.add(item);
                        return false;
                    }
                    return true;
                }).forEach(item -> {
                    List<DealPrice> prices = getPrice(item, 1);
                    // 거래량이 0이면 하루 전으로 계산
                    int lastDayIndex = prices.get(0).getVolume() == 0 ? 1 : 0;
                    // 마지막 가격
                    DealPrice price = prices.get(lastDayIndex);

                    // 당일 현재(종)가가 기대 매도 가격보다 높으면 하한 가격 및 기대 가격 갱신
                    while (price.getClose() != 0 && item.getExpectedSellingPrice() != 0
                            && price.getClose() >= item.getExpectedSellingPrice()) item.sellingPriceUpdate(price.getDate());
                    // 현재 종가(현재가)가 하한 매도 가격 대비 같거나 낮으면 삭제
                    if (price.getClose() <= item.getMinimumSellingPrice()) updateItems.add(item);
                });
    }
}