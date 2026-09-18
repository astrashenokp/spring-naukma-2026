package com.example.lab03;

public interface TeaBatchService {

	TeaBatchResponse registerBatch(RegisterTeaBatchRequest request);

	TeaBatchResponse getBatch(String id);

	TeaBatchResponse updateStatus(String id, UpdateTeaBatchStatusRequest request);

}
