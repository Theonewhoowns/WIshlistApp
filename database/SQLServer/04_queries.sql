-- 1. Списки и стоимость оставшихся покупок. Выполнять как отдельный запрос.
SELECT w.wishlist_id,u.username,w.name,r.name AS recipient_name,
 COUNT(i.wish_item_id) AS item_count,
 SUM(CASE WHEN i.is_purchased=1 THEN 1 ELSE 0 END) AS purchased_count,
 COALESCE(SUM(CASE WHEN i.is_purchased=0 THEN i.planned_price ELSE 0 END),0) AS remaining_cost
FROM [wishlistApp].wishlist w
JOIN [wishlistApp].app_user u ON u.user_id=w.owner_id
LEFT JOIN [wishlistApp].recipient r ON r.recipient_id=w.recipient_id AND r.owner_id=w.owner_id
LEFT JOIN [wishlistApp].wish_item i ON i.wishlist_id=w.wishlist_id
GROUP BY w.wishlist_id,u.username,w.name,r.name
ORDER BY w.wishlist_id;

-- 2. Подбор: ivan (2), Мама (1), бюджет 5000, универсальный повод.
-- В JDBC значения заменим параметрами PreparedStatement.
;WITH params AS (
 SELECT 2 AS user_id,1 AS recipient_id,CAST(5000 AS DECIMAL(12,2)) AS budget,
 CAST('ANY' AS VARCHAR(20)) AS occasion
), candidates AS (
 SELECT g.*,
 (SELECT COUNT(*) FROM [wishlistApp].gift_idea_interest gi
  JOIN [wishlistApp].recipient_interest ri ON ri.interest_id=gi.interest_id
  WHERE gi.gift_idea_id=g.gift_idea_id AND ri.recipient_id=r.recipient_id AND ri.owner_id=r.owner_id) AS matched_count,
 CASE WHEN EXISTS(SELECT 1 FROM [wishlistApp].recipient_interest ri
                  WHERE ri.recipient_id=r.recipient_id AND ri.owner_id=r.owner_id) THEN 1 ELSE 0 END AS has_interests
 FROM params p
 JOIN [wishlistApp].recipient r ON r.recipient_id=p.recipient_id AND r.owner_id=p.user_id
 JOIN [wishlistApp].gift_idea g ON g.is_active=1
  AND r.age BETWEEN g.min_age AND g.max_age
  AND g.max_price<=p.budget AND g.occasion IN ('ANY',p.occasion)
)
SELECT gift_idea_id,title,min_price,max_price,matched_count
FROM candidates WHERE has_interests=0 OR matched_count>0
ORDER BY matched_count DESC,max_price,gift_idea_id;
