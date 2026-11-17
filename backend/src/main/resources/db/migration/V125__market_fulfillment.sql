-- V125: Skill Market fulfillment (GĐ5, BA §8.4 / Q8 chốt 18/11).
-- A request is FULFILLED when the requester accepts a linked APPROVED skill,
-- or auto-confirmed after 7 days of silence if the linked skill still meets
-- the bar (APPROVED). Follows the bounty decision flow discussed 16/11
-- (requester decides in 7 days, auto-award on silence).
-- fulfillment_status: OPEN | PENDING_CONFIRM | FULFILLED.
-- Auto-confirm is evaluated lazily (on refreshSkillRequest / request detail
-- read); a nightly scheduled job for it is still pending (see BA §9.5).

ALTER TABLE skill_requests
    ADD COLUMN fulfilled_skill_id BIGINT REFERENCES skills_master(id),
    ADD COLUMN fulfillment_status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    ADD COLUMN fulfillment_deadline TIMESTAMPTZ,
    ADD COLUMN fulfilled_at TIMESTAMPTZ;

CREATE INDEX idx_skill_requests_fulfillment
    ON skill_requests (fulfillment_status, fulfillment_deadline);
