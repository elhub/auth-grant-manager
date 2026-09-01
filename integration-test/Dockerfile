FROM docker.jfrog.elhub.cloud/frzq0sxltynr/devxp/elhub-playwright:latest

WORKDIR /app

COPY integration-test/package.json integration-test/package-lock.json integration-test/
RUN cd integration-test && npm ci

COPY integration-test/authorization-document-flow.sh integration-test/bankid-sign.ts integration-test/
RUN chmod +x integration-test/authorization-document-flow.sh \
  && chown -R 65532:65532 /app

USER 65532

CMD ["bash", "integration-test/authorization-document-flow.sh"]
