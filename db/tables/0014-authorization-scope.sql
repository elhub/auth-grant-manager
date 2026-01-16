--changeset elhub:14
CREATE TABLE auth.authorization_scope
(
  id                       UUID                     DEFAULT gen_random_uuid() NOT NULL,
  authorized_resource_type auth.authorization_resource                        NOT NULL,
  authorized_resource_id   VARCHAR(64)                                        NOT NULL,
  permission_type          auth.permission_type                               NOT NULL,
  created_at               TIMESTAMP WITH TIME ZONE DEFAULT now()             NOT NULL,
  PRIMARY KEY (id)
);
