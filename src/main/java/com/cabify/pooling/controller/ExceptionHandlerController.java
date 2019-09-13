package com.cabify.pooling.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import com.cabify.pooling.exception.GroupAlreadyExistsException;

import lombok.extern.slf4j.Slf4j;

@ControllerAdvice
@Slf4j
public class ExceptionHandlerController {

    @ExceptionHandler({ GroupAlreadyExistsException.class })
    public ResponseEntity<Void> handleAlreadyExistsException(GroupAlreadyExistsException ex, WebRequest request) {
    	log.warn(ex.getMessage(), ex);
    	return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }
}
