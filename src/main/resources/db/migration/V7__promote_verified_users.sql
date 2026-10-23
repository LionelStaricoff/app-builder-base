-- Quien completa el KYC puede publicar (decisión de producto B).
--
-- El webhook de Didit ahora promueve USER -> CREATOR al aprobar la
-- verificación, pero las verificaciones que ya existían antes de ese cambio
-- quedaron en USER: son usuarios verificados que no podían publicar y no
-- tenían forma de destrabarlo desde la interfaz (el CTA los mandaba a un
-- formulario que los rebotaba).
--
-- Idempotente por construcción: solo toca USER con verificación VERIFIED.
-- NO revierte CREATOR: un creador conserva sus experiencias, sus ventas y el
-- acceso a cobros aunque su verificación caduque después.
UPDATE users
SET role = 'CREATOR'
WHERE role = 'USER'
  AND id IN (SELECT user_id FROM didit_verifications WHERE status = 'VERIFIED');
