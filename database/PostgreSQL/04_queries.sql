-- Просмотр списков со стоимостью оставшихся покупок.
SELECT w.wishlist_id,u.username,w.name,r.name AS recipient_name,
 count(i.wish_item_id) AS item_count,
 count(i.wish_item_id) FILTER (WHERE i.is_purchased) AS purchased_count,
 coalesce(sum(i.planned_price) FILTER (WHERE NOT i.is_purchased),0) AS remaining_cost
FROM "wishlistApp".wishlist w
JOIN "wishlistApp".app_user u ON u.user_id=w.owner_id
LEFT JOIN "wishlistApp".recipient r ON r.recipient_id=w.recipient_id AND r.owner_id=w.owner_id
LEFT JOIN "wishlistApp".wish_item i ON i.wishlist_id=w.wishlist_id
GROUP BY w.wishlist_id,u.username,r.name
ORDER BY w.wishlist_id;

-- Пример подбора: пользователь ivan (2), получатель Мама (1), бюджет 5000.
-- В JDBC значения 2, 1, 5000 и ANY заменим параметрами PreparedStatement.
WITH params AS (
 SELECT 2 AS user_id,1 AS recipient_id,5000::numeric AS budget,'ANY'::text AS occasion
), candidates AS (
 SELECT g.*, (
   SELECT count(*) FROM "wishlistApp".gift_idea_interest gi
   JOIN "wishlistApp".recipient_interest ri ON ri.interest_id=gi.interest_id
   WHERE gi.gift_idea_id=g.gift_idea_id AND ri.recipient_id=r.recipient_id AND ri.owner_id=r.owner_id
 ) AS matched_count,
 EXISTS(SELECT 1 FROM "wishlistApp".recipient_interest ri WHERE ri.recipient_id=r.recipient_id AND ri.owner_id=r.owner_id) AS has_interests
 FROM params p
 JOIN "wishlistApp".recipient r ON r.recipient_id=p.recipient_id AND r.owner_id=p.user_id
 JOIN "wishlistApp".gift_idea g ON g.is_active
  AND r.age BETWEEN g.min_age AND g.max_age
  AND g.max_price<=p.budget AND g.occasion IN ('ANY',p.occasion)
)
SELECT gift_idea_id,title,min_price,max_price,matched_count
FROM candidates WHERE NOT has_interests OR matched_count>0
ORDER BY matched_count DESC,max_price,gift_idea_id;
