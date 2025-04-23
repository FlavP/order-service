package com.polarbookshop.orderservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polarbookshop.orderservice.book.Book;
import com.polarbookshop.orderservice.book.BookClient;
import com.polarbookshop.orderservice.domain.Order;
import com.polarbookshop.orderservice.domain.OrderStatus;
import com.polarbookshop.orderservice.order.event.OrderAcceptedMessage;
import com.polarbookshop.orderservice.web.OrderRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.OutputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestChannelBinderConfiguration.class)
@Testcontainers
class OrderServiceApplicationTests {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17.4"));
	@Autowired
	private ObjectMapper objectMapper;
	@Autowired
	private OutputDestination output;
	@Autowired
	private WebTestClient webTestClient;
	@MockitoBean
	private BookClient bookClient;

	@DynamicPropertySource
	static void postgresqlProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.r2dbc.url", OrderServiceApplicationTests::r2dbcUrl);
		registry.add("spring.r2dbc.username", postgres::getUsername);
		registry.add("spring.r2dbc.password", postgres::getPassword);
		registry.add("spring.flyway.url", postgres::getJdbcUrl);
	}

	private static String r2dbcUrl() {
		return String.format("r2dbc:postgresql://%s:%s/%s", postgres.getHost(),
				postgres.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT), postgres.getDatabaseName());
	}

	@Test
	void whenGetOrdersThenReturn() throws IOException {
		String bookIsbn = "123456789";
		Book book = new Book(bookIsbn, "Title", "Author", 9.90);
		given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));
		OrderRequest orderRequest = new OrderRequest(bookIsbn, 1);
		Order expectedOrder = webTestClient.post().uri("/orders")
				.bodyValue(orderRequest)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();
		assertThat(expectedOrder).isNotNull();
		assertThat(objectMapper.readValue(output.receive().getPayload(), OrderAcceptedMessage.class))
				.isEqualTo(new OrderAcceptedMessage(expectedOrder.id()));

		webTestClient.get().uri("/orders")
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBodyList(Order.class).value(orders -> {
					assertThat(orders.stream().filter(order -> order.bookIsbn().equals(bookIsbn)).findAny()).isNotEmpty();
				});
	}

	@Test
	void whenPostRequestAndBookExistsThenOrderAccepted() throws IOException {
		String bookIsbn = "1234567899";
		Book book = new Book(bookIsbn, "Title", "Author", 9.90);
		given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.just(book));
		OrderRequest orderRequest = new OrderRequest(bookIsbn, 3);

		Order createOrder = webTestClient.post().uri("/orders")
				.bodyValue(orderRequest)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();

		assertThat(createOrder).isNotNull();
		assertThat(createOrder.bookIsbn()).isEqualTo(orderRequest.isbn());
		assertThat(createOrder.quantity()).isEqualTo(orderRequest.quantity());
		assertThat(createOrder.bookName()).isEqualTo(book.title() + " - " + book.author());
		assertThat(createOrder.bookPrice()).isEqualTo(book.price());
		assertThat(createOrder.status()).isEqualTo(OrderStatus.ACCEPTED);

		assertThat(objectMapper.readValue(output.receive().getPayload(), OrderAcceptedMessage.class))
				.isEqualTo(new OrderAcceptedMessage(createOrder.id()));
	}

	@Test
	void whenPostRequestAndBookNotExistsThenOrderRejected() {
		String bookIsbn = "1234567894";
		given(bookClient.getBookByIsbn(bookIsbn)).willReturn(Mono.empty());
		OrderRequest orderRequest = new OrderRequest(bookIsbn, 3);

		Order createdOrder = webTestClient.post().uri("/orders")
				.bodyValue(orderRequest)
				.exchange()
				.expectStatus().is2xxSuccessful()
				.expectBody(Order.class).returnResult().getResponseBody();

		assertThat(createdOrder).isNotNull();
		assertThat(createdOrder.bookIsbn()).isEqualTo(orderRequest.isbn());
		assertThat(createdOrder.quantity()).isEqualTo(orderRequest.quantity());
		assertThat(createdOrder.status()).isEqualTo(OrderStatus.REJECTED);
	}
}
