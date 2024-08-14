package com.matdang.seatdang.payment.service;

import com.matdang.seatdang.payment.dto.*;
import com.matdang.seatdang.payment.entity.PayApprove;
import com.matdang.seatdang.payment.entity.PayReady;
import com.matdang.seatdang.payment.entity.RefundResult;
import com.matdang.seatdang.payment.repository.KakaoPayRepository;
import com.matdang.seatdang.payment.repository.PayApproveRepository;
import com.matdang.seatdang.payment.repository.RefundRepository;
import com.matdang.seatdang.payment.service.dto.ApproveRequest;
import com.matdang.seatdang.payment.service.dto.ReadyRequest;
import com.matdang.seatdang.payment.service.dto.RefundRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class KakaoPayService {
    private final KakaoPayRepository kakaoPayRepository;
    private final PayApproveRepository payApproveRepository;
    private final RefundRepository refundRepository;

    @Value("${kakao.secret.key}")
    private String secretKey;
    @Value("${cid}")
    private String cid;
    private static final String KAKAO_PAY_READY_URL = "https://open-api.kakaopay.com/online/v1/payment/ready";
    private static final String KAKAO_PAY_APPROVE_URL = "https://open-api.kakaopay.com/online/v1/payment/approve";
    private static final String KAKAO_PAY_CANCEL_URL = "https://open-api.kakaopay.com/online/v1/payment/cancel";

    public ReadyResponse ready(PayDetail payDetail) {

        // 1. 헤더 설정
        HttpHeaders headers = initHttpHeaders();

        // 2. 요청 본문 설정
        ReadyRequest readyRequest = createReady(payDetail);

        // 3. HttpEntity 생성
        HttpEntity<ReadyRequest> entityMap = new HttpEntity<>(readyRequest, headers);

        // 4. RestTemplate 사용
        ResponseEntity<ReadyResponse> response = new RestTemplate().postForEntity(
                KAKAO_PAY_READY_URL,
                entityMap,
                ReadyResponse.class);

        ReadyResponse readyResponse = response.getBody();

        // TODO : 로직 오류 가능성 존재
        // 저장
        kakaoPayRepository.save(PayReady.builder()
                .partnerOrderId(readyRequest.getPartnerOrderId())
                .shopId(payDetail.getShopId())
                .tid(readyResponse.getTid())
                .partnerUserId(readyRequest.getPartnerUserId()).build());

        // 5. 응답 처리
        return readyResponse;
    }



    public Object approve(ReadyRedirect readyRedirect) {
        // ready할 때 저장해놓은 TID로 승인 요청
        // Call “Execute approved payment” API by pg_token, TID mapping to the current payment transaction and other parameters.
        HttpHeaders headers = initHttpHeaders();


        PayReady findPayReady = kakaoPayRepository.findById(readyRedirect.getPartnerOrderId()).get();

        log.debug("tid = {}", findPayReady.getTid());
        log.debug("PartnerOrderId = {}", readyRedirect.getPartnerOrderId());
        log.debug("pg_token = {}", readyRedirect.getPg_token());

        // Request param
        ApproveRequest approveRequest = createApprove(readyRedirect, findPayReady);

        // Send Request
        HttpEntity<ApproveRequest> entityMap = new HttpEntity<>(approveRequest, headers);
        try {
            ResponseEntity<PayApprove> response = new RestTemplate().postForEntity(
                    KAKAO_PAY_APPROVE_URL,
                    entityMap,
                    PayApprove.class
            );

            // 승인 결과를 저장한다.
            // save the result of approval
            PayApprove approveResponse = createApproveResult(response, findPayReady);
            payApproveRepository.save(approveResponse);

            return approveResponse;
        } catch (HttpStatusCodeException ex) {
            return ex.getResponseBodyAs(ApproveFail.class);
        }
    }

    public Object refund(RefundDetail refundDetail) {
        HttpHeaders headers = initHttpHeaders();
        log.debug("refundDetail= {}", refundDetail);

        // Request param
        RefundRequest refundRequest = RefundRequest.builder()
                .cid(cid)
                .tid(refundDetail.getTid())
                .cancelAmount(refundDetail.getCancelAmount())
                .cancelTaxFreeAmount(refundDetail.getCancelTaxFreeAmount())
                .build();
        log.debug("refundRequest ={}", refundRequest);

        //취소 결과(result of approval)
        //{"tid":"T6a8d38329fd342cdd38",
        // "cid":"TC0ONETIME",
        // "status":"PART_CANCEL_PAYMENT",
        // "partner_order_id":"1",
        // "partner_user_id":"1",
        // "payment_method_type":"CARD",
        // "item_name":"초코파이",
        // "aid":"A6a8d6a129fd342cdd3c",
        // "quantity":1,
        // "amount":{"total":1100000,
        // "tax_free":0,"vat":100000,
        // "point":0,"discount":0,
        // "green_deposit":0},
        // "canceled_amount":{"total":1100,"tax_free":0,"vat":100,"point":0,"discount":0,"green_deposit":0},
        // "cancel_available_amount":{"total":1098900,"tax_free":0,"vat":99900,"point":0,"discount":0,"green_deposit":0},
        // "approved_cancel_amount":{"total":1100,"tax_free":0,"vat":100,"point":0,"discount":0,"green_deposit":0},
        // "created_at":"2024-07-30T20:50:27",
        // "approved_at":"2024-07-30T20:51:01",
        // "canceled_at":"2024-07-30T21:03:45"}
        // Send Request
        HttpEntity<RefundRequest> entityMap = new HttpEntity<>(refundRequest, headers);
        try {
            ResponseEntity<RefundResult> response = new RestTemplate().postForEntity(
                    KAKAO_PAY_CANCEL_URL,
                    entityMap,
                    RefundResult.class
            );

            // 취소 결과를 저장한다.
            // save the result of cancel
            RefundResult refundResult = createRefundResult(response, refundDetail);
            refundRepository.save(refundResult);

            return refundResult;
        } catch (HttpStatusCodeException ex) {
            return ex.getResponseBodyAs(ApproveFail.class);
        }
    }


    private HttpHeaders initHttpHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "DEV_SECRET_KEY " + secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ReadyRequest createReady(PayDetail payDetail) {
        return ReadyRequest.builder()
                .cid(cid)
                .partnerOrderId(String.valueOf(payDetail.getPartnerOrderId()))
                .partnerUserId(payDetail.getPartnerUserId())
                .itemName(payDetail.getItemName())
                .quantity(payDetail.getQuantity())
                .totalAmount(payDetail.getTotalAmount())
                .taxFreeAmount(payDetail.getTaxFreeAmount())
                .approvalUrl("http://localhost:8080/" + "payment/approve" + "?PartnerOrderId="
                        + payDetail.getPartnerOrderId()) // 결제 성공 시 redirect url, 최대 255자
                .cancelUrl("http://localhost:8080/" + "payment/cancel") // 결제 취소 시 redirect url, 최대 255자
                .failUrl("http://localhost:8080/" + "payment/fail") //결제 실패 시 redirect url, 최대 255자

                .build();
    }

    // TODO : 메서드 중복 제거하기
    private static PayApprove createApproveResult(ResponseEntity<PayApprove> response, PayReady findPayReady) {
        PayApprove approveResponse = response.getBody();
        approveResponse.registerShopId(findPayReady.getShopId());
        return approveResponse;
    }

    private RefundResult createRefundResult(ResponseEntity<RefundResult> response, RefundDetail refundDetail) {
        RefundResult refundResult = response.getBody();
        refundResult.registerShopId(refundDetail.getShopId());
        return refundResult;
    }

    private ApproveRequest createApprove(ReadyRedirect readyRedirect, PayReady findPayReady) {
        ApproveRequest approveRequest = ApproveRequest.builder()
                .cid(cid)
                .tid(findPayReady.getTid())
                .partnerOrderId(readyRedirect.getPartnerOrderId())
                .partnerUserId(findPayReady.getPartnerUserId())
                .pgToken(readyRedirect.getPg_token())
                .build();
        return approveRequest;
    }


}