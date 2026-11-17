package dev.commerceflow.trade.catalog;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import static dev.commerceflow.trade.catalog.CatalogTypes.*;

@Service @Profile("database")
public class CatalogAvailability {
    private final CatalogRepository repository;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public CatalogAvailability(CatalogRepository repository, org.springframework.jdbc.core.JdbcTemplate jdbc) { this.repository=repository; this.jdbc=jdbc; }
    // The future order transaction must call this injected bean and keep its locks through commit.
    @Transactional(propagation=Propagation.MANDATORY)
    public Quote requireSellable(long skuId,int quantity) {
        if(quantity<1 || quantity>999) throw new Invalid();
        var ids=jdbc.queryForList("SELECT product_id FROM catalog_sku WHERE id=?",Long.class,skuId);
        if(ids.isEmpty()) throw new Missing();
        var product=repository.product(ids.getFirst(),true);
        // Current locking read avoids reading an earlier RR snapshot after acquiring the parent lock.
        var rows=jdbc.queryForList("SELECT name,price_cents,enabled FROM catalog_sku WHERE id=? AND product_id=? FOR UPDATE",skuId,product.id());
        if(rows.isEmpty()) throw new Missing();
        var sku=rows.getFirst();
        if(!product.published() || !Boolean.TRUE.equals(sku.get("enabled"))) throw new Conflict("CATALOG_NOT_SELLABLE");
        long unit=((Number)sku.get("price_cents")).longValue();
        long total;
        try { total=Math.multiplyExact(unit,quantity); } catch(ArithmeticException e) {throw new Invalid();}
        return new Quote(Long.toString(product.id()),Long.toString(skuId),(String)sku.get("name"),Long.toString(unit),quantity,
            Long.toString(total),"CNY",Long.toString(product.version()));
    }
}
