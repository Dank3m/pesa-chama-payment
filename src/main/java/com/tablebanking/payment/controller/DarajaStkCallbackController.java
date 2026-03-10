package com.tablebanking.payment.controller;

import com.tablebanking.payment.service.CollectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/daraja/stk")
@RequiredArgsConstructor
@Slf4j
public class DarajaStkCallbackController {

    private final CollectionService collectionService;

    /**
     * Callback endpoint for Daraja STK Push results.
     * Safaricom sends the payment result to this endpoint after a customer
     * completes, cancels, or times out on an STK Push prompt.
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, String>> handleStkCallback(@RequestBody Map<String, Object> payload) {
        log.info("Received STK Push callback: {}", payload);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = (Map<String, Object>) payload.get("Body");
            @SuppressWarnings("unchecked")
            Map<String, Object> stkCallback = (Map<String, Object>) body.get("stkCallback");

            String merchantRequestId = (String) stkCallback.get("MerchantRequestID");
            String checkoutRequestId = (String) stkCallback.get("CheckoutRequestID");
            int resultCode = ((Number) stkCallback.get("ResultCode")).intValue();
            String resultDesc = (String) stkCallback.get("ResultDesc");

            String mpesaReceiptNumber = null;

            if (resultCode == 0) {
                @SuppressWarnings("unchecked")
                Map<String, Object> callbackMetadata = (Map<String, Object>) stkCallback.get("CallbackMetadata");
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items = (List<Map<String, Object>>) callbackMetadata.get("Item");

                for (Map<String, Object> item : items) {
                    String name = (String) item.get("Name");
                    if ("MpesaReceiptNumber".equals(name)) {
                        mpesaReceiptNumber = (String) item.get("Value");
                    }
                }
            }

            log.info("STK Callback parsed: merchantRequestId={}, checkoutRequestId={}, resultCode={}, resultDesc={}, receipt={}",
                    merchantRequestId, checkoutRequestId, resultCode, resultDesc, mpesaReceiptNumber);

            collectionService.handleStkCallback(checkoutRequestId, resultCode, resultDesc, mpesaReceiptNumber);

        } catch (Exception e) {
            log.error("Error processing STK Push callback: {}", e.getMessage(), e);
        }

        return ResponseEntity.ok(Map.of("ResultCode", "0", "ResultDesc", "Success"));
    }
}
