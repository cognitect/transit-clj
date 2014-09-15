## Deployment

Running `build/package` will build transit-clj, install to the local
maven reposistory, and prepare for deployment.

Running `build/deploy` will run `build/package`, and push to the
'datomic-maven' S3 bucket.  To deploy, you'll need the AWS CLI tools
installed and configured with permissions to access this bucket (see
https://aws.amazon.com/cli/).
