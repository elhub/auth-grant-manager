FROM docker.jfrog.elhub.cloud/frzq0sxltynr/devxp/elhub-playwright:1.64.0-noble

RUN apt-get update && apt-get install -y jq tini && rm -rf /var/lib/apt/lists/*

# The Playwright base configures Node trust; curl also needs the internal roots.
RUN cp /etc/playwright/combined_certs.crt /usr/local/share/ca-certificates/elhub.crt \
    && update-ca-certificates

WORKDIR /app

COPY integration-test/package.json integration-test/package-lock.json integration-test/
RUN cd integration-test && npm ci

COPY integration-test/*.sh integration-test/bankid-sign.ts integration-test/
COPY integration-test/lib/ integration-test/lib/
RUN chmod +x integration-test/*.sh \
  && chown -R 65532:65532 /app

USER 65532

ENTRYPOINT ["/usr/bin/tini", "-g", "--"]
CMD ["bash", "integration-test/run.sh"]
