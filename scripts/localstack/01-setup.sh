#!/bin/bash
set -e

echo "Creating S3 buckets..."
awslocal s3 mb s3://triaige-processed-documents
awslocal s3 mb s3://triaige-results

echo "Seeding test document..."
echo "Contrato de prestação de serviços entre as partes João Silva (Contratante) e 
Empresa ABC Ltda (Contratada). O contratante alega descumprimento contratual, 
com falta de pagamento de 3 parcelas no valor de R\$ 5.000,00 cada.
Data do contrato: 01/01/2025. Vencimento das parcelas: 01/02/2025, 01/03/2025, 01/04/2025." \
  > /tmp/normalized.txt

awslocal s3 cp /tmp/normalized.txt \
  s3://triaige-processed-documents/tenant-001/sess-2026-000001/doc-001/normalized.txt

echo "LocalStack setup complete."
