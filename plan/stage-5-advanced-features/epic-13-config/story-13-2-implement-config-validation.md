# Story 13-2: Implement Config Validation

## Story

As a developer, I want to implement Config Validation so that configuration can be validated.

## Acceptance Criteria

- [ ] validate() method validates config
- [ ] All settings validated
- [ ] Invalid config throws exception
- [ ] Validation errors reported clearly
- [ ] Unit tests verify all methods

## Technical Details

### Validation Rules

- dataDir must be a valid directory
- tableMaxSize must be > 0
- leafPageMaxSize must be > 0
- indexPageMaxSize must be > 0
- batchSize must be between 1 and 1024
- timeWindow must be between 1 and 10000
- maxVersions must be between 1 and 100
- occupancyThreshold must be between 0 and 1

## Testing

- testValidateValidConfig()
- testValidateInvalidDataDir()
- testValidateInvalidTableMaxSize()
- testValidateInvalidBatchSize()
- testValidateMultipleErrors()

## Effort Estimate

1 day
