package com.example.lab03;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeaBatchServiceImplTest {

	@Mock
	private TeaBatchRepository teaBatchRepository;

	@Mock
	private ShelfLifeStrategy greenTeaStrategy;

	@Test
	void shouldRegisterTeaBatchSuccessfully() {
		when(greenTeaStrategy.getTeaType()).thenReturn(TeaType.GREEN);
		when(greenTeaStrategy.calculateShelfLifeDays()).thenReturn(365);

		TeaBatchServiceImpl service = new TeaBatchServiceImpl(teaBatchRepository, List.of(greenTeaStrategy));

		RegisterTeaBatchRequest request = new RegisterTeaBatchRequest("TB-1", TeaType.GREEN, "Sun Valley Tea");

		when(teaBatchRepository.existsById("TB-1")).thenReturn(false);
		when(teaBatchRepository.save(any(TeaBatch.class))).thenAnswer(invocation -> invocation.getArgument(0));

		TeaBatchResponse response = service.registerBatch(request);

		assertNotNull(response);
		assertEquals("TB-1", response.id());
		assertEquals(365, response.shelfLifeDays());
		assertEquals(TeaBatchStatus.IN_STOCK, response.status());
		verify(teaBatchRepository).save(any(TeaBatch.class));
	}

	@Test
	void shouldThrowDuplicateTeaBatchExceptionWhenIdAlreadyExists() {
		when(greenTeaStrategy.getTeaType()).thenReturn(TeaType.GREEN);

		TeaBatchServiceImpl service = new TeaBatchServiceImpl(teaBatchRepository, List.of(greenTeaStrategy));

		RegisterTeaBatchRequest request = new RegisterTeaBatchRequest("TB-1", TeaType.GREEN, "Sun Valley Tea");

		when(teaBatchRepository.existsById("TB-1")).thenReturn(true);

		assertThrows(DuplicateTeaBatchException.class, () -> service.registerBatch(request));

		verify(teaBatchRepository, never()).save(any());
	}

	@Test
	void shouldThrowInvalidTeaBatchStateExceptionOnIllegalTransition() {
		when(greenTeaStrategy.getTeaType()).thenReturn(TeaType.GREEN);

		TeaBatchServiceImpl service = new TeaBatchServiceImpl(teaBatchRepository, List.of(greenTeaStrategy));

		TeaBatch discontinued = new TeaBatch("TB-2", TeaType.GREEN, "Sun Valley Tea", 365, TeaBatchStatus.DISCONTINUED);
		when(teaBatchRepository.findById("TB-2")).thenReturn(Optional.of(discontinued));

		UpdateTeaBatchStatusRequest request = new UpdateTeaBatchStatusRequest(TeaBatchStatus.IN_STOCK);

		assertThrows(InvalidTeaBatchStateException.class, () -> service.updateStatus("TB-2", request));

		verify(teaBatchRepository, never()).save(any());
	}

	@Test
	void shouldThrowTeaBatchNotFoundExceptionWhenBatchDoesNotExist() {
		when(greenTeaStrategy.getTeaType()).thenReturn(TeaType.GREEN);

		TeaBatchServiceImpl service = new TeaBatchServiceImpl(teaBatchRepository, List.of(greenTeaStrategy));

		when(teaBatchRepository.findById("UNKNOWN")).thenReturn(Optional.empty());

		assertThrows(TeaBatchNotFoundException.class, () -> service.getBatch("UNKNOWN"));
	}

}
