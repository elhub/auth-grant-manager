FROM mcr.microsoft.com/playwright:v1.60.0-jammy

RUN apt-get update && apt-get install -y curl jq ca-certificates && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY integration-test/package.json integration-test/package-lock.json integration-test/
RUN cd integration-test && npm ci

COPY integration-test/authorization-document-flow.sh integration-test/bankid-sign.ts integration-test/
RUN chmod +x integration-test/authorization-document-flow.sh

CMD ["bash", "integration-test/authorization-document-flow.sh"]
