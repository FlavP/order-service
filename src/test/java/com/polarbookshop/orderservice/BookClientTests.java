package com.polarbookshop.orderservice;

import com.polarbookshop.orderservice.book.Book;
import com.polarbookshop.orderservice.book.BookClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.shaded.com.google.common.net.HttpHeaders;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.IOException;

public class BookClientTests {
    private MockWebServer webServer;
    private BookClient client;

    @BeforeEach
    void setup() throws IOException {
        this.webServer = new MockWebServer();
        this.webServer.start();
        var  webClient = WebClient.builder()
                .baseUrl(webServer.url("/").uri().toString())
                .build();
        this.client = new BookClient(webClient);
    }

    @AfterEach
    void clean() throws IOException {
        this.webServer.shutdown();
    }

    @Test
    void whenBookExistsThenReturnBook() {
        String isbn = "1234567890";
        var mockResponse = new MockResponse()
                .addHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .setBody("""
                        {
                            "isbn": %s,
                            "title": "Title",
                            "author": "Author",
                            "price": 9.90,
                            "publisher": "Polarsophia"
                        }
                        """.formatted(isbn));
        webServer.enqueue(mockResponse);
        Mono<Book> book = client.getBookByIsbn(isbn);
        StepVerifier.create(book)
                .expectNextMatches(
                        b -> b.isbn().equals(isbn)
                ).verifyComplete();
    }
}
