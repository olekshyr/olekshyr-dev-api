package dev.olekshyr.api.blog;

import dev.olekshyr.api.shared.ApiException;
import dev.olekshyr.api.shared.ErrorCode;
import org.springframework.http.HttpStatus;

class TagNotFoundException extends ApiException {

  TagNotFoundException(long id) {
    super(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "No tag with id " + id);
  }
}
