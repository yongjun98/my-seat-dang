package com.matdang.seatdang.payment.controller;


import com.matdang.seatdang.payment.dto.PayDetail;
import com.matdang.seatdang.payment.dto.ReadyResponse;
import com.matdang.seatdang.payment.service.KakaoPayService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@RequiredArgsConstructor
@Controller
@RequestMapping("/payment")
public class KakaoPayController {

    private final KakaoPayService kakaoPayService;

    @GetMapping("/main")
    public void pay() {
    }

    private static int count = 0;

    @GetMapping("/request")
    public String readyToKakaoPay(Model model, HttpSession session) {
        PayDetail payDetail = (PayDetail) model.getAttribute("PayDetail");
        // test code
        payDetail= PayDetail.builder()
                .itemName("초코파이")
                .partnerUserId("1")
                .partnerOrderId("1")
                .taxFreeAmount(0)
                .quantity(2)
                .totalAmount(2000+count)
                .build();
        count++;
        // end ==
        ReadyResponse readyResponse = kakaoPayService.ready(payDetail);
        log.info("==== payment request ====");
        // pc
        model.addAttribute("response", readyResponse);

        session.setAttribute("readyResponse", readyResponse);
        log.debug("session id: {}", session.getId());
        log.debug("Stored in session: {}", session.getAttribute("readyResponse"));
        log.debug("tid =  {}",((ReadyResponse) session.getAttribute("readyResponse")).getTid());

        return "payment/ready";
    }

    @GetMapping("/approve")
    public String approve(@RequestParam("pg_token") String pgToken,HttpSession session, Model model) {
        log.debug("=== approve start ===");
        ReadyResponse response = (ReadyResponse) session.getAttribute("readyResponse");
        log.debug("approve tid = {}", response.getTid());
        String approveResponse = kakaoPayService.approve(response.getTid(), pgToken);
        log.info("=== payment approve ===");
        model.addAttribute("response", approveResponse);
        return  "payment/approve";
    }

//    @GetMapping("/readyToKakaoPay/refund/{openType}")
//    public String refund( @PathVariable("openType") String openType, Model model) {
//        String refundRequest = kakaoPayService.refund();
//        model.addAttribute("response", refundRequest);
//        return  openType + "/refund";
//    }
//
//    @GetMapping("/cancel/{openType}")
//    public String cancel( @PathVariable("openType") String openType) {
//        // 주문건이 진짜 취소되었는지 확인 후 취소 처리
//        // 결제내역조회(/v1/payment/status) api에서 status를 확인한다.
//        // To prevent the unwanted request cancellation caused by attack,
//        // the “show payment status” API is called and then check if the status is QUIT_PAYMENT before suspending the payment
//        return  openType + "/cancel";
//    }
//
//    @GetMapping("/fail/{agent}/{openType}")
//    public String fail(@PathVariable("openType") String openType) {
//        // 주문건이 진짜 실패되었는지 확인 후 실패 처리
//        // 결제내역조회(/v1/payment/status) api에서 status를 확인한다.
//        // To prevent the unwanted request cancellation caused by attack,
//        // the “show payment status” API is called and then check if the status is FAIL_PAYMENT before suspending the payment
//        return openType + "/fail";
//    }
}
