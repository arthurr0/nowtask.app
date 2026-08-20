package app.nowtask.tasks.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import app.nowtask.tasks.SearchService;
import app.nowtask.tasks.SearchService.SearchResult;

@RestController
@RequestMapping("/api/search")
class SearchController {

    private final SearchService search;

    SearchController(SearchService search) {
        this.search = search;
    }

    @GetMapping
    SearchResult search(@RequestParam(name = "q", required = false) String query) {
        return search.search(query);
    }
}
