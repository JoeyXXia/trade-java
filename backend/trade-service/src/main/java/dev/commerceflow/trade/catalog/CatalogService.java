package dev.commerceflow.trade.catalog;

import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static dev.commerceflow.trade.catalog.CatalogTypes.*;

@Service @Profile("database")
public class CatalogService {
    private final CatalogRepository repo;
    private final CatalogAvailability availability;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;
    public CatalogService(CatalogRepository repo,CatalogAvailability availability,org.springframework.jdbc.core.JdbcTemplate jdbc) {this.repo=repo;this.availability=availability;this.jdbc=jdbc;}
    private long actor(){return Long.parseLong(((JwtAuthenticationToken)SecurityContextHolder.getContext().getAuthentication()).getToken().getSubject());}
    @Transactional(readOnly=true) public Page list(int page,int size,String sort){return repo.page(page,size,sort,false);}
    @Transactional(readOnly=true) public Product detail(long id){var row=repo.product(id,false);if(!row.published())throw new Missing();return repo.detail(row,false);}
    @Transactional(readOnly=true) @PreAuthorize("hasRole('ADMIN')")
    public Page adminList(int page,int size,String sort){return repo.page(page,size,sort,true);}
    @Transactional(readOnly=true) @PreAuthorize("hasRole('ADMIN')")
    public Product adminDetail(long id){return repo.detail(repo.product(id,false),true);}
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Product create(Create input){long id=repo.create(input,actor());return repo.detail(repo.product(id,false),true);}
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Product edit(long id,Edit input){
        var row=repo.product(id,true);repo.expected(row,input.version());
        if(input.published() && jdbc.queryForObject("SELECT COUNT(*) FROM catalog_sku WHERE product_id=? AND enabled=TRUE",Long.class,id)==0)
            throw new Conflict("CATALOG_NO_ACTIVE_SKU");
        jdbc.update("UPDATE catalog_product SET title=?,description=?,published=? WHERE id=?",input.title().trim(),input.description(),input.published(),id);
        repo.version(id,actor());return repo.detail(repo.product(id,true),true);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Product addSku(long id,SkuWrite input){
        var row=repo.product(id,true);repo.expected(row,input.version());long price=number(input.priceCents(),true);
        try {jdbc.update("INSERT INTO catalog_sku(product_id,sku_code,name,price_cents,enabled,created_by,updated_by) VALUES (?,?,?,?,?,?,?)",
            id,input.skuCode(),input.name().trim(),price,input.enabled(),actor(),actor());}
        catch(DuplicateKeyException e){throw new Conflict("SKU_CODE_UNAVAILABLE");}
        repo.version(id,actor());return repo.detail(repo.product(id,true),true);
    }
    @Transactional @PreAuthorize("hasRole('ADMIN')")
    public Product editSku(long id,long skuId,SkuWrite input){
        var row=repo.product(id,true);repo.expected(row,input.version());long price=number(input.priceCents(),true);
        var codes=jdbc.queryForList("SELECT sku_code FROM catalog_sku WHERE id=? AND product_id=? FOR UPDATE",String.class,skuId,id);
        if(codes.isEmpty())throw new Missing();
        if(!codes.getFirst().equals(input.skuCode()))throw new Invalid();
        if(row.published() && !input.enabled() && jdbc.queryForObject("SELECT COUNT(*) FROM catalog_sku WHERE product_id=? AND enabled=TRUE AND id<>?",Long.class,id,skuId)==0)
            throw new Conflict("CATALOG_NO_ACTIVE_SKU");
        jdbc.update("UPDATE catalog_sku SET name=?,price_cents=?,enabled=?,updated_by=? WHERE id=? AND product_id=?",
            input.name().trim(),price,input.enabled(),actor(),skuId,id);
        repo.version(id,actor());return repo.detail(repo.product(id,true),true);
    }
    @Transactional public Quote quote(long skuId,int quantity){return availability.requireSellable(skuId,quantity);}
}
