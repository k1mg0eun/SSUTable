package com.g1j2.ssutable.core;

/** 식사 모드 예약에 함께 발급되는 식권. */
public final class Ticket {
    public enum Status {
        UNUSED("미사용"),
        CANCELLED("취소됨");

        public final String label;

        Status(String label) {
            this.label = label;
        }
    }

    public final String id;
    public final String name;
    public final long issuedAt;
    public final String seatId;
    public Status status;

    public Ticket(String id, String name, long issuedAt, String seatId, Status status) {
        this.id = id;
        this.name = name;
        this.issuedAt = issuedAt;
        this.seatId = seatId;
        this.status = status;
    }

    /** QR에 담는 내용. 식권 확인 단말이 읽는다는 가정. */
    public String qrPayload() {
        return "SSUTABLE|TICKET|" + id + "|" + seatId + "|" + issuedAt;
    }
}
