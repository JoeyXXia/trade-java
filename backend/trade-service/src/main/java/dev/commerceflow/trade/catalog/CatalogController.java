package dev.commerceflow.trade.catalog;

import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import static dev.commerceflow.trade.catalog.CatalogTypes.*;

@RestController @Profile("database")
public class CatalogController {
    private final CatalogService service;
    public CatalogController(CatalogService service){this.service=service;}
    @GetMapping("/api/catalog/products") public Page list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size,@RequestParam(defaultValue="newest") String sort){return service.list(page,size,sort);}
    @GetMapping("/api/catalog/products/{id}") public Product detail(@PathVariable long id){return service.detail(id);}
    @PostMapping("/api/catalog/skus/{id}/quote") public Quote quote(@PathVariable long id,@Valid @RequestBody Quantity input){return service.quote(id,input.quantity());}
    @GetMapping("/api/admin/catalog/products") public Page adminList(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="12") int size,@RequestParam(defaultValue="newest") String sort){return service.adminList(page,size,sort);}
    @GetMapping("/api/admin/catalog/products/{id}") public Product adminDetail(@PathVariable long id){return service.adminDetail(id);}
    @PostMapping("/api/admin/catalog/products") @ResponseStatus(HttpStatus.CREATED) public Product create(@Valid @RequestBody Create input){return service.create(input);}
    @PutMapping("/api/admin/catalog/products/{id}") public Product edit(@PathVariable long id,@Valid @RequestBody Edit input){return service.edit(id,input);}
    @PostMapping("/api/admin/catalog/products/{id}/skus") @ResponseStatus(HttpStatus.CREATED) public Product addSku(@PathVariable long id,@Valid @RequestBody SkuWrite input){return service.addSku(id,input);}
    @PutMapping("/api/admin/catalog/products/{id}/skus/{skuId}") public Product editSku(@PathVariable long id,@PathVariable long skuId,@Valid @RequestBody SkuWrite input){return service.editSku(id,skuId,input);}
}
